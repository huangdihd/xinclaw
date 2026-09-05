package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.claw.trace.AgentTraceEvent;
import xin.claw.trace.AgentTracePublisher;

final class OpenAiCompatibleReasoningStreamingChatLanguageModelTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<JsonObject> requests = new ArrayList<>();
    private final AtomicReference<IntFunction<String>> responseFactory = new AtomicReference<>();
    private final AtomicReference<IntFunction<Integer>> statusFactory = new AtomicReference<>();
    private final AtomicReference<IntPredicate> disconnectBeforeHeaders = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        responseFactory.set(index -> index == 1 ? firstToolCallStream() : finalAnswerStream());
        statusFactory.set(index -> 200);
        disconnectBeforeHeaders.set(index -> false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/chat/completions", exchange -> {
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            synchronized (requests) { requests.add(request); }
            int index = requestCount.incrementAndGet();
            if (disconnectBeforeHeaders.get().test(index)) {
                exchange.close();
                return;
            }
            String body = responseFactory.get().apply(index);
            byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(statusFactory.get().apply(index), encoded.length);
            exchange.getResponseBody().write(encoded);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void reasoningFingerprintHasFixedSizeAndDoesNotRetainMessageText() {
        String sensitiveText = "large-sensitive-response-" + "x".repeat(10_000);
        String fingerprint = OpenAiCompatibleReasoningStreamingChatLanguageModel.messageFingerprint(
            AiMessage.from(sensitiveText));

        assertEquals(64, fingerprint.length());
        assertFalse(fingerprint.contains("large-sensitive-response"));
    }

    @Test
    void reasoningSidecarEvictsLeastRecentlyUsedMessages() {
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model-v4", Duration.ofSeconds(5), "high",
            new AgentTracePublisher(System::nanoTime)
        );
        AiMessage first = AiMessage.from("message-0");
        model.rememberReasoning(first, "reasoning-0");
        for (int index = 1; index <= OpenAiCompatibleReasoningStreamingChatLanguageModel.MAX_REASONING_ENTRIES; index++) {
            model.rememberReasoning(AiMessage.from("message-" + index), "reasoning-" + index);
        }

        assertEquals(OpenAiCompatibleReasoningStreamingChatLanguageModel.MAX_REASONING_ENTRIES,
            model.reasoningCacheSize());
        assertNull(model.reasoningFor(first), "oldest reasoning entry must be evicted");
        assertEquals("reasoning-256", model.reasoningFor(AiMessage.from("message-256")));
    }

    @Test
    void preservesReasoningStreamsVisibleTextAndReplaysReasoningWithToolResults() throws Exception {
        AgentTracePublisher publisher = new AgentTracePublisher(System::nanoTime);
        List<AgentTraceEvent> trace = new ArrayList<>();
        publisher.subscribe(trace::add);
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model-v4", Duration.ofSeconds(5), "high", publisher
        );
        ToolSpecification tool = ToolSpecification.builder().name("whereAmI").description("position").build();

        CapturingHandler first = new CapturingHandler();
        model.generate(List.of(UserMessage.from("Where am I?")), List.of(tool), first);
        assertTrue(first.done.await(5, TimeUnit.SECONDS));
        assertNull(first.error.get());
        assertEquals("I will inspect.", first.tokens.toString());
        AiMessage firstMessage = first.response.get().content();
        assertTrue(firstMessage.hasToolExecutionRequests());
        ToolExecutionRequest request = firstMessage.toolExecutionRequests().get(0);
        assertEquals("whereAmI", request.name());
        assertEquals("{}", request.arguments());

        CapturingHandler second = new CapturingHandler();
        model.generate(List.<ChatMessage>of(
            UserMessage.from("Where am I?"),
            firstMessage,
            ToolExecutionResultMessage.from("call-1", "whereAmI", "x=1 y=64 z=2")
        ), List.of(tool), second);
        assertTrue(second.done.await(5, TimeUnit.SECONDS));
        assertNull(second.error.get(), String.valueOf(second.error.get()));
        assertNotNull(second.response.get());
        assertEquals("You are at the recorded position.", second.response.get().content().text());

        JsonObject firstPayload = requests.get(0);
        assertFalse(firstPayload.has("thinking"));
        assertEquals("high", firstPayload.get("reasoning_effort").getAsString());
        assertTrue(firstPayload.get("stream").getAsBoolean());
        JsonObject replayedAssistant = requests.get(1).getAsJsonArray("messages").get(1).getAsJsonObject();
        assertEquals("Inspect the live position.", replayedAssistant.get("reasoning_content").getAsString());
        assertEquals("x=1 y=64 z=2", requests.get(1).getAsJsonArray("messages").get(2).getAsJsonObject().get("content").getAsString());

        assertEquals(3L, trace.stream().filter(row -> row.eventType().equals("model_reasoning_delta")).count());
        assertTrue(trace.stream().anyMatch(row -> row.eventType().equals("model_response")
            && row.payload().get("reasoning").getAsString().equals("Inspect the live position.")));
    }

    @Test
    void retriesOneReasoningOnlyTerminalResponseWithoutInventingAssistantText() throws Exception {
        responseFactory.set(index -> index == 1 ? reasoningOnlyStream() : firstToolCallStream());
        AgentTracePublisher publisher = new AgentTracePublisher(System::nanoTime);
        List<AgentTraceEvent> trace = new ArrayList<>();
        publisher.subscribe(trace::add);
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model-v4", Duration.ofSeconds(5), "high", publisher
        );
        ToolSpecification tool = ToolSpecification.builder().name("whereAmI").description("position").build();
        CapturingHandler handler = new CapturingHandler();

        model.generate(List.of(UserMessage.from("Where am I?")), List.of(tool), handler);

        assertTrue(handler.done.await(5, TimeUnit.SECONDS));
        assertNull(handler.error.get(), String.valueOf(handler.error.get()));
        assertEquals(2, requestCount.get());
        assertTrue(handler.response.get().content().hasToolExecutionRequests());
        assertTrue(trace.stream().anyMatch(row -> row.eventType().equals("model_empty_response_retry")));
    }

    @Test
    void acceptsToolCallResponseWithNoVisibleAssistantContent() throws Exception {
        responseFactory.set(index -> toolCallOnlyStream());
        AgentTracePublisher publisher = new AgentTracePublisher(System::nanoTime);
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model-v4", Duration.ofSeconds(5), "high", publisher
        );
        ToolSpecification tool = ToolSpecification.builder().name("whereAmI").description("position").build();
        CapturingHandler handler = new CapturingHandler();

        model.generate(List.of(UserMessage.from("Where am I?")), List.of(tool), handler);

        assertTrue(handler.done.await(5, TimeUnit.SECONDS));
        assertNull(handler.error.get(), String.valueOf(handler.error.get()));
        assertTrue(handler.response.get().content().hasToolExecutionRequests());
        assertEquals("", handler.tokens.toString());
    }

    @Test
    void retriesTransientHttpStatusThenSucceeds() throws Exception {
        statusFactory.set(index -> index == 1 ? 503 : 200);
        responseFactory.set(index -> index == 1 ? "temporarily unavailable" : firstToolCallStream());
        AgentTracePublisher publisher = new AgentTracePublisher(System::nanoTime);
        List<AgentTraceEvent> trace = new ArrayList<>();
        publisher.subscribe(trace::add);
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model", Duration.ofSeconds(5), "high", publisher
        );
        CapturingHandler handler = new CapturingHandler();

        model.generate(List.of(UserMessage.from("Where am I?")), List.of(
            ToolSpecification.builder().name("whereAmI").description("position").build()), handler);

        assertTrue(handler.done.await(8, TimeUnit.SECONDS));
        assertNull(handler.error.get(), String.valueOf(handler.error.get()));
        assertEquals(2, requestCount.get());
        assertTrue(handler.response.get().content().hasToolExecutionRequests());
        assertTrue(trace.stream().anyMatch(row -> row.eventType().equals("model_retry")
            && row.payload().get("reason").getAsString().equals("http_503")));
    }

    @Test
    void retriesConnectionClosedBeforeHeadersThenSucceeds() throws Exception {
        disconnectBeforeHeaders.set(index -> index == 1);
        responseFactory.set(index -> firstToolCallStream());
        AgentTracePublisher publisher = new AgentTracePublisher(System::nanoTime);
        List<AgentTraceEvent> trace = new ArrayList<>();
        publisher.subscribe(trace::add);
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model", Duration.ofSeconds(5), "high", publisher
        );
        CapturingHandler handler = new CapturingHandler();

        model.generate(List.of(UserMessage.from("Where am I?")), List.of(
            ToolSpecification.builder().name("whereAmI").description("position").build()), handler);

        assertTrue(handler.done.await(8, TimeUnit.SECONDS));
        assertNull(handler.error.get(), String.valueOf(handler.error.get()));
        assertEquals(2, requestCount.get());
        assertTrue(handler.response.get().content().hasToolExecutionRequests());
        assertTrue(trace.stream().anyMatch(row -> row.eventType().equals("model_retry")
            && row.payload().get("reason").getAsString().equals("transport_io")));
    }

    @Test
    void doesNotRetryPermanentHttp400() throws Exception {
        statusFactory.set(index -> 400);
        responseFactory.set(index -> "invalid messages");
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model", Duration.ofSeconds(5), "high",
            new AgentTracePublisher(System::nanoTime)
        );
        CapturingHandler handler = new CapturingHandler();

        model.generate(List.of(UserMessage.from("Where am I?")), handler);

        assertTrue(handler.done.await(3, TimeUnit.SECONDS));
        assertNotNull(handler.error.get());
        assertEquals(1, requestCount.get());
    }

    @Test
    void retainsLatestUserMessageWhenWindowContainsOnlyToolHistory() throws Exception {
        statusFactory.set(index -> requests.get(index - 1).getAsJsonArray("messages").asList().stream()
            .anyMatch(message -> message.getAsJsonObject().get("role").getAsString().equals("user")) ? 200 : 400);
        responseFactory.set(index -> finalAnswerStream());
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model", Duration.ofSeconds(5), "high",
            new AgentTracePublisher(System::nanoTime)
        );

        CapturingHandler first = new CapturingHandler();
        model.generate(List.of(UserMessage.from("Enter the target building.")), first);
        assertTrue(first.done.await(3, TimeUnit.SECONDS));
        assertNull(first.error.get(), String.valueOf(first.error.get()));

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
            .id("call-evicted-user")
            .name("whereAmI")
            .arguments("{}")
            .build();
        CapturingHandler continued = new CapturingHandler();
        model.generate(List.of(
            AiMessage.from(List.of(toolCall)),
            ToolExecutionResultMessage.from("call-evicted-user", "whereAmI", "x=1 y=64 z=2")
        ), continued);

        assertTrue(continued.done.await(3, TimeUnit.SECONDS));
        assertNull(continued.error.get(), String.valueOf(continued.error.get()));
        JsonObject anchored = requests.get(1).getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals("user", anchored.get("role").getAsString());
        assertEquals("Enter the target building.", anchored.get("content").getAsString());
    }

    @Test
    void exhaustsAfterThreeTransientHttpFailures() throws Exception {
        statusFactory.set(index -> 503);
        responseFactory.set(index -> "temporarily unavailable");
        AgentTracePublisher publisher = new AgentTracePublisher(System::nanoTime);
        List<AgentTraceEvent> trace = new ArrayList<>();
        publisher.subscribe(trace::add);
        OpenAiCompatibleReasoningStreamingChatLanguageModel model = new OpenAiCompatibleReasoningStreamingChatLanguageModel(
            "secret", baseUrl, "reasoning-model", Duration.ofSeconds(5), "high", publisher
        );
        CapturingHandler handler = new CapturingHandler();

        model.generate(List.of(UserMessage.from("Where am I?")), handler);

        assertTrue(handler.done.await(6, TimeUnit.SECONDS));
        assertNotNull(handler.error.get());
        assertEquals(3, requestCount.get());
        assertEquals(2, trace.stream().filter(row -> row.eventType().equals("model_retry")).count());
    }

    @Test
    void classifiesHttpTimeoutAsRetryableTransport() {
        assertTrue(OpenAiCompatibleReasoningStreamingChatLanguageModel.isRetryableTransport(
            new CompletionException(new HttpTimeoutException("timed out"))));
        assertFalse(OpenAiCompatibleReasoningStreamingChatLanguageModel.isRetryableTransport(
            new IllegalArgumentException("bad request")));
    }

    private static String firstToolCallStream() {
        return String.join("\n",
            "data: {\"id\":\"r1\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"reasoning_content\":\"Inspect the \"}}]}",
            "data: {\"id\":\"r1\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"reasoning_content\":\"live position.\"}}]}",
            "data: {\"id\":\"r1\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"content\":\"I will inspect.\"}}]}",
            "data: {\"id\":\"r1\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"whereAmI\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":8,\"total_tokens\":18}}",
            "data: [DONE]", "");
    }

    private static String finalAnswerStream() {
        return String.join("\n",
            "data: {\"id\":\"r2\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"reasoning_content\":\"Use the result.\"}}]}",
            "data: {\"id\":\"r2\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"content\":\"You are at the recorded position.\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":9,\"total_tokens\":29}}",
            "data: [DONE]", "");
    }

    private static String reasoningOnlyStream() {
        return String.join("\n",
            "data: {\"id\":\"empty-1\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"reasoning_content\":\"I should call a tool.\"}}]}",
            "data: {\"id\":\"empty-1\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
            "data: [DONE]", "");
    }

    private static String toolCallOnlyStream() {
        return String.join("\n",
            "data: {\"id\":\"tool-only\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"reasoning_content\":\"Call the position tool.\"}}]}",
            "data: {\"id\":\"tool-only\",\"model\":\"reasoning-model-v4\",\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-only\",\"type\":\"function\",\"function\":{\"name\":\"whereAmI\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}]}",
            "data: [DONE]", "");
    }

    private static final class CapturingHandler implements StreamingResponseHandler<AiMessage> {
        private final StringBuilder tokens = new StringBuilder();
        private final AtomicReference<Response<AiMessage>> response = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final CountDownLatch done = new CountDownLatch(1);
        @Override public void onNext(String token) { tokens.append(token); }
        @Override public void onComplete(Response<AiMessage> value) { response.set(value); done.countDown(); }
        @Override public void onError(Throwable value) { error.set(value); done.countDown(); }
    }
}
