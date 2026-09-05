package xin.claw;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import xin.claw.trace.AgentTracePublisher;

/**
 * OpenAI-compatible thinking SSE adapter that preserves reasoning_content.
 *
 * LangChain4j 0.35 discards that provider extension. This adapter keeps it
 * in a sidecar keyed by the exact assistant message and replays it on later
 * tool-aware requests for OpenAI-compatible reasoning protocols.
 */
final class OpenAiCompatibleReasoningStreamingChatLanguageModel implements StreamingChatLanguageModel {
    private static final Gson GSON = new Gson();
    private static final int MAX_RETRIES = 2;
    static final int MAX_REASONING_ENTRIES = 256;
    private static final long RETRY_BASE_DELAY_MILLIS = 500L;
    private final String apiKey;
    private final URI endpoint;
    private final String modelName;
    private final String reasoningEffort;
    private final AgentTracePublisher trace;
    private final HttpClient client;
    private final Map<String, String> reasoningByMessage = java.util.Collections.synchronizedMap(
        new LinkedHashMap<>(MAX_REASONING_ENTRIES + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX_REASONING_ENTRIES;
            }
        }
    );
    private final java.util.concurrent.atomic.AtomicReference<UserMessage> latestUserMessage =
        new java.util.concurrent.atomic.AtomicReference<>();

    OpenAiCompatibleReasoningStreamingChatLanguageModel(
        String apiKey,
        String baseUrl,
        String modelName,
        Duration timeout,
        String reasoningEffort,
        AgentTracePublisher trace
    ) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.endpoint = URI.create(normalizeBaseUrl(baseUrl) + "/chat/completions");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.reasoningEffort = PluginConfig.normalizeReasoningEffort(reasoningEffort);
        this.trace = Objects.requireNonNull(trace, "trace");
        this.client = HttpClient.newBuilder()
            .connectTimeout(Objects.requireNonNull(timeout, "timeout"))
            .build();
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, List.of(), handler);
    }

    @Override
    public void generate(
        List<ChatMessage> messages,
        ToolSpecification tool,
        StreamingResponseHandler<AiMessage> handler
    ) {
        generate(messages, List.of(tool), handler);
    }

    @Override
    public void generate(
        List<ChatMessage> messages,
        List<ToolSpecification> tools,
        StreamingResponseHandler<AiMessage> handler
    ) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(handler, "handler");
        JsonObject requestBody = requestBody(messages, tools);
        JsonObject traceRequest = new JsonObject();
        traceRequest.addProperty("model", modelName);
        traceRequest.addProperty("reasoning_effort", reasoningEffort);
        traceRequest.add("request", requestBody.deepCopy());
        trace.emit("model_request", traceRequest);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(Math.max(10L, PluginConfig.apiTimeoutSeconds)))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
            .build();
        AtomicBoolean terminal = new AtomicBoolean(false);
        sendRequest(request, requestBody, handler, terminal, 0);
    }

    private void sendRequest(
        HttpRequest request,
        JsonObject requestBody,
        StreamingResponseHandler<AiMessage> handler,
        AtomicBoolean terminal,
        int retryAttempt
    ) {
        client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenAcceptAsync(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String body = readFully(response.body());
                    if (retryableHttpStatus(response.statusCode()) && retryAttempt < MAX_RETRIES) {
                        scheduleRetry(request, requestBody, handler, terminal, retryAttempt,
                            "http_" + response.statusCode());
                        return;
                    }
                    fail(handler, terminal, new IllegalStateException(
                        "Model API HTTP " + response.statusCode() + ": " + body
                    ));
                    return;
                }
                try (InputStream stream = response.body()) {
                    parseStream(stream, handler, terminal);
                } catch (Throwable error) {
                    if (error instanceof EmptyModelResponseException empty && retryAttempt < MAX_RETRIES) {
                        JsonObject retry = new JsonObject();
                        retry.addProperty("attempt", retryAttempt + 1);
                        retry.addProperty("response_id", empty.responseId);
                        retry.addProperty("reasoning_chars", empty.reasoningChars);
                        retry.addProperty("finish_reason", empty.finishReason);
                        trace.emit("model_empty_response_retry", retry);
                        scheduleRetry(request, requestBody, handler, terminal, retryAttempt,
                            "empty_" + (empty.finishReason == null ? "unknown" : empty.finishReason));
                        return;
                    }
                    if (isRetryableTransport(error) && retryAttempt < MAX_RETRIES) {
                        scheduleRetry(request, requestBody, handler, terminal, retryAttempt, "transport_io");
                        return;
                    }
                    fail(handler, terminal, error);
                }
            })
            .exceptionally(error -> {
                if (isRetryableTransport(error) && retryAttempt < MAX_RETRIES) {
                    scheduleRetry(request, requestBody, handler, terminal, retryAttempt, "transport_io");
                } else {
                    fail(handler, terminal, error);
                }
                return null;
            });
    }

    private void scheduleRetry(
        HttpRequest request,
        JsonObject requestBody,
        StreamingResponseHandler<AiMessage> handler,
        AtomicBoolean terminal,
        int retryAttempt,
        String reason
    ) {
        if (terminal.get()) return;
        int nextAttempt = retryAttempt + 1;
        long delayMillis = RETRY_BASE_DELAY_MILLIS << retryAttempt;
        JsonObject retry = new JsonObject();
        retry.addProperty("attempt", nextAttempt + 1);
        retry.addProperty("retry_attempt", nextAttempt);
        retry.addProperty("reason", reason);
        retry.addProperty("delay_millis", delayMillis);
        trace.emit("model_retry", retry);
        JsonObject retriedRequest = new JsonObject();
        retriedRequest.addProperty("model", modelName);
        retriedRequest.addProperty("reasoning_effort", reasoningEffort);
        retriedRequest.addProperty("retry_attempt", nextAttempt);
        retriedRequest.addProperty("retry_reason", reason);
        retriedRequest.add("request", requestBody.deepCopy());
        trace.emit("model_request", retriedRequest);
        CompletableFuture.runAsync(
            () -> sendRequest(request, requestBody, handler, terminal, nextAttempt),
            CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS)
        );
    }

    static boolean retryableHttpStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    static boolean isRetryableTransport(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IOException) return true;
            if (current instanceof CompletionException && current.getCause() == current) break;
            current = current.getCause();
        }
        return false;
    }

    private JsonObject requestBody(List<ChatMessage> messages, List<ToolSpecification> tools) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelName);
        payload.addProperty("stream", true);
        JsonObject streamOptions = new JsonObject();
        streamOptions.addProperty("include_usage", true);
        payload.add("stream_options", streamOptions);
        if (!"none".equals(reasoningEffort)) {
            payload.addProperty("reasoning_effort", reasoningEffort);
        }
        UserMessage newestUser = null;
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage user) newestUser = user;
        }
        if (newestUser != null) latestUserMessage.set(newestUser);

        JsonArray serializedMessages = new JsonArray();
        UserMessage anchoredUser = newestUser == null ? latestUserMessage.get() : null;
        boolean anchorInserted = false;
        for (ChatMessage message : messages) {
            if (anchoredUser != null && !anchorInserted && !(message instanceof SystemMessage)) {
                serializedMessages.add(serializeMessage(anchoredUser));
                anchorInserted = true;
            }
            serializedMessages.add(serializeMessage(message));
        }
        if (anchoredUser != null && !anchorInserted) serializedMessages.add(serializeMessage(anchoredUser));
        payload.add("messages", serializedMessages);
        if (!tools.isEmpty()) {
            JsonArray serializedTools = new JsonArray();
            for (ToolSpecification tool : tools) serializedTools.add(serializeTool(tool));
            payload.add("tools", serializedTools);
        }
        return payload;
    }

    private JsonObject serializeMessage(ChatMessage message) {
        JsonObject output = new JsonObject();
        if (message instanceof SystemMessage system) {
            output.addProperty("role", "system");
            output.addProperty("content", system.text());
        } else if (message instanceof UserMessage user) {
            if (!user.hasSingleText()) throw new IllegalArgumentException("OpenAI-compatible adapter supports text-only user messages");
            output.addProperty("role", "user");
            output.addProperty("content", user.singleText());
            if (user.name() != null && !user.name().isBlank()) output.addProperty("name", user.name());
        } else if (message instanceof AiMessage ai) {
            output.addProperty("role", "assistant");
            output.addProperty("content", ai.text() == null ? "" : ai.text());
            String reasoning = reasoningFor(ai);
            if (reasoning != null) output.addProperty("reasoning_content", reasoning);
            if (ai.hasToolExecutionRequests()) {
                JsonArray calls = new JsonArray();
                for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                    JsonObject call = new JsonObject();
                    call.addProperty("id", request.id());
                    call.addProperty("type", "function");
                    JsonObject function = new JsonObject();
                    function.addProperty("name", request.name());
                    function.addProperty("arguments", request.arguments());
                    call.add("function", function);
                    calls.add(call);
                }
                output.add("tool_calls", calls);
            }
        } else if (message instanceof ToolExecutionResultMessage result) {
            output.addProperty("role", "tool");
            output.addProperty("tool_call_id", result.id());
            output.addProperty("name", result.toolName());
            output.addProperty("content", result.text());
        } else {
            throw new IllegalArgumentException("Unsupported chat message: " + message.getClass().getName());
        }
        return output;
    }

    private static JsonObject serializeTool(ToolSpecification specification) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "function");
        JsonObject function = new JsonObject();
        function.addProperty("name", specification.name());
        if (specification.description() != null) function.addProperty("description", specification.description());
        function.add("parameters", serializeParameters(specification.parameters()));
        wrapper.add("function", function);
        return wrapper;
    }

    private static JsonObject serializeParameters(ToolParameters parameters) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", parameters == null || parameters.type() == null ? "object" : parameters.type());
        JsonObject properties = new JsonObject();
        if (parameters != null && parameters.properties() != null) {
            for (Map.Entry<String, Map<String, Object>> entry : parameters.properties().entrySet()) {
                properties.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
            }
        }
        schema.add("properties", properties);
        if (parameters != null && parameters.required() != null && !parameters.required().isEmpty()) {
            schema.add("required", GSON.toJsonTree(parameters.required()));
        }
        return schema;
    }

    private void parseStream(
        InputStream stream,
        StreamingResponseHandler<AiMessage> handler,
        AtomicBoolean terminal
    ) throws Exception {
        StreamState state = new StreamState();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) break;
                JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                consumeChunk(chunk, state, handler);
            }
        }
        if (state.content.toString().isBlank() && state.tools.isEmpty()) {
            throw new EmptyModelResponseException(
                state.responseId,
                state.reasoning.length(),
                state.finishReason
            );
        }
        AiMessage message = state.toMessage();
        String reasoning = state.reasoning.toString();
        rememberReasoning(message, reasoning);
        JsonObject responseTrace = state.tracePayload(message, reasoning);
        trace.emit("model_response", responseTrace);
        if (terminal.compareAndSet(false, true)) {
            handler.onComplete(new Response<>(
                message,
                state.tokenUsage(),
                state.finishReason(),
                Map.of(
                    "response_id", state.responseId == null ? "" : state.responseId,
                    "model", state.responseModel == null ? modelName : state.responseModel,
                    "reasoning_content", reasoning
                )
            ));
        }
    }

    private void consumeChunk(
        JsonObject chunk,
        StreamState state,
        StreamingResponseHandler<AiMessage> handler
    ) {
        if (chunk.has("id") && !chunk.get("id").isJsonNull()) state.responseId = chunk.get("id").getAsString();
        if (chunk.has("model") && !chunk.get("model").isJsonNull()) state.responseModel = chunk.get("model").getAsString();
        if (chunk.has("usage") && chunk.get("usage").isJsonObject()) state.usage = chunk.getAsJsonObject("usage").deepCopy();
        JsonArray choices = chunk.has("choices") && chunk.get("choices").isJsonArray()
            ? chunk.getAsJsonArray("choices") : new JsonArray();
        for (JsonElement choiceElement : choices) {
            JsonObject choice = choiceElement.getAsJsonObject();
            if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                state.finishReason = choice.get("finish_reason").getAsString();
            }
            JsonObject delta = choice.has("delta") && choice.get("delta").isJsonObject()
                ? choice.getAsJsonObject("delta") : new JsonObject();
            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                String text = delta.get("reasoning_content").getAsString();
                state.reasoning.append(text);
                trace.emit("model_reasoning_delta", textPayload(text));
            }
            if (delta.has("content") && !delta.get("content").isJsonNull()) {
                String text = delta.get("content").getAsString();
                state.content.append(text);
                trace.emit("model_content_delta", textPayload(text));
                handler.onNext(text);
            }
            if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                for (JsonElement callElement : delta.getAsJsonArray("tool_calls")) {
                    state.consumeToolDelta(callElement.getAsJsonObject());
                }
            }
        }
    }

    private static JsonObject textPayload(String text) {
        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        return payload;
    }

    private void fail(
        StreamingResponseHandler<AiMessage> handler,
        AtomicBoolean terminal,
        Throwable error
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error_type", error.getClass().getName());
        payload.addProperty("message", String.valueOf(error.getMessage()));
        trace.emit("model_error", payload);
        if (terminal.compareAndSet(false, true)) handler.onError(error);
    }

    static String messageFingerprint(AiMessage message) {
        StringBuilder raw = new StringBuilder(message.text() == null ? "" : message.text());
        if (message.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : message.toolExecutionRequests()) {
                raw.append('\u0000').append(request.id())
                    .append('\u0000').append(request.name())
                    .append('\u0000').append(request.arguments());
            }
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    void rememberReasoning(AiMessage message, String reasoning) {
        reasoningByMessage.put(messageFingerprint(message), reasoning);
    }

    String reasoningFor(AiMessage message) {
        return reasoningByMessage.get(messageFingerprint(message));
    }

    int reasoningCacheSize() {
        synchronized (reasoningByMessage) {
            return reasoningByMessage.size();
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = Objects.requireNonNull(baseUrl, "baseUrl").trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String readFully(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "<unreadable response: " + error.getMessage() + ">";
        }
    }

    private static final class ToolCallAccumulator {
        private String id;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }

    private static final class EmptyModelResponseException extends RuntimeException {
        private final String responseId;
        private final int reasoningChars;
        private final String finishReason;

        private EmptyModelResponseException(String responseId, int reasoningChars, String finishReason) {
            super("Model returned reasoning but no assistant content or tool calls");
            this.responseId = responseId;
            this.reasoningChars = reasoningChars;
            this.finishReason = finishReason;
        }
    }

    private static final class StreamState {
        private String responseId;
        private String responseModel;
        private String finishReason;
        private JsonObject usage;
        private final StringBuilder reasoning = new StringBuilder();
        private final StringBuilder content = new StringBuilder();
        private final Map<Integer, ToolCallAccumulator> tools = new LinkedHashMap<>();

        private void consumeToolDelta(JsonObject delta) {
            int index = delta.has("index") ? delta.get("index").getAsInt() : tools.size();
            ToolCallAccumulator call = tools.computeIfAbsent(index, ignored -> new ToolCallAccumulator());
            if (delta.has("id") && !delta.get("id").isJsonNull()) call.id = delta.get("id").getAsString();
            if (delta.has("function") && delta.get("function").isJsonObject()) {
                JsonObject function = delta.getAsJsonObject("function");
                if (function.has("name") && !function.get("name").isJsonNull()) call.name.append(function.get("name").getAsString());
                if (function.has("arguments") && !function.get("arguments").isJsonNull()) call.arguments.append(function.get("arguments").getAsString());
            }
        }

        private List<ToolExecutionRequest> toolRequests() {
            return tools.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(entry -> {
                    ToolCallAccumulator call = entry.getValue();
                    return ToolExecutionRequest.builder()
                        .id(call.id)
                        .name(call.name.toString())
                        .arguments(call.arguments.toString())
                        .build();
                })
                .toList();
        }

        private AiMessage toMessage() {
            List<ToolExecutionRequest> requests = toolRequests();
            if (requests.isEmpty()) return AiMessage.from(content.toString());
            if (content.toString().isBlank()) return AiMessage.from(requests);
            return AiMessage.from(content.toString(), requests);
        }

        private TokenUsage tokenUsage() {
            if (usage == null) return null;
            return new TokenUsage(
                integer(usage, "prompt_tokens"),
                integer(usage, "completion_tokens"),
                integer(usage, "total_tokens")
            );
        }

        private static Integer integer(JsonObject object, String key) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : null;
        }

        private FinishReason finishReason() {
            if (finishReason == null) return FinishReason.OTHER;
            return switch (finishReason) {
                case "stop" -> FinishReason.STOP;
                case "length" -> FinishReason.LENGTH;
                case "tool_calls", "function_call" -> FinishReason.TOOL_EXECUTION;
                case "content_filter" -> FinishReason.CONTENT_FILTER;
                default -> FinishReason.OTHER;
            };
        }

        private JsonObject tracePayload(AiMessage message, String reasoningText) {
            JsonObject payload = new JsonObject();
            if (responseId != null) payload.addProperty("response_id", responseId);
            if (responseModel != null) payload.addProperty("model", responseModel);
            payload.addProperty("reasoning", reasoningText);
            payload.addProperty("content", content.toString());
            payload.addProperty("finish_reason", finishReason);
            if (usage != null) payload.add("usage", usage.deepCopy());
            JsonArray calls = new JsonArray();
            if (message.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : message.toolExecutionRequests()) {
                    JsonObject call = new JsonObject();
                    call.addProperty("id", request.id());
                    call.addProperty("tool", request.name());
                    call.addProperty("arguments", request.arguments());
                    calls.add(call);
                }
            }
            payload.add("tool_calls", calls);
            return payload;
        }
    }
}
