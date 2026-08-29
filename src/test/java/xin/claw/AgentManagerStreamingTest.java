package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class AgentManagerStreamingTest {
    @Test
    void botAgentPublishesTokenStreamAndBuildsStreamingModel() throws Exception {
        assertEquals(
            TokenStream.class,
            AgentManager.BotAgent.class.getMethod("chat", String.class).getReturnType()
        );
        assertInstanceOf(SingleTerminalStreamingChatLanguageModel.class, AgentManager.buildStreamingModel());
    }

    @Test
    void collectsStreamedTokensAndStartsExactlyOnce() {
        FakeTokenStream stream = new FakeTokenStream(false, true);

        String response = AgentManager.collectTokenStream(stream);

        assertEquals("hello world", response);
        assertEquals(1, stream.starts.get());
    }

    @Test
    void fallsBackToCompletedAiMessageWhenProviderEmitsNoTokens() {
        FakeTokenStream stream = new FakeTokenStream(false, false);

        assertEquals("hello world", AgentManager.collectTokenStream(stream));
    }

    @Test
    void completedFinalResponseWinsOverIntermediateStreamedText() {
        FakeTokenStream stream = new FakeTokenStream(false, true, "final answer");

        assertEquals("final answer", AgentManager.collectTokenStream(stream));
    }

    @Test
    void ignoresIntermediateCompletionThatStillContainsToolRequests() {
        assertEquals(
            "final answer",
            AgentManager.collectTokenStream(new IntermediateCompletionTokenStream())
        );
    }

    @Test
    void propagatesStreamingErrors() {
        RuntimeException error = assertThrows(
            RuntimeException.class,
            () -> AgentManager.collectTokenStream(new FakeTokenStream(true, false))
        );

        assertTrue(error.getMessage().contains("boom") || (error.getCause() != null
            && String.valueOf(error.getCause().getMessage()).contains("boom")));
    }

    @Test
    void interruptionWaitsForTerminalCallbackBeforeReturning() throws Exception {
        ManualTokenStream stream = new ManualTokenStream();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                AgentManager.collectTokenStream(stream);
            } catch (Throwable error) {
                thrown.set(error);
            }
        });
        worker.start();
        stream.started.await();

        worker.interrupt();
        Thread.sleep(50);
        assertTrue(worker.isAlive(), "interrupted collector must retain the processing slot until terminal callback");

        stream.complete("done");
        worker.join(1000);
        assertFalse(worker.isAlive());
        assertNotNull(thrown.get());
        assertTrue(String.valueOf(thrown.get().getMessage()).contains("interrupted"));
    }

    @Test
    void processingSlotIsNeverForceReleasedBeforeStreamTerminates() throws Exception {
        Thread active = new Thread();
        AtomicReference<Thread> slot = new AtomicReference<>(active);
        AtomicInteger warnings = new AtomicInteger();
        Thread waiter = new Thread(() -> AgentManager.waitForProcessingThreadToFinish(
            slot, active, 0, warnings::incrementAndGet
        ));
        waiter.start();

        Thread.sleep(50);
        assertTrue(waiter.isAlive());
        assertSame(active, slot.get());

        slot.compareAndSet(active, null);
        waiter.join(1000);
        assertFalse(waiter.isAlive());
        assertTrue(warnings.get() >= 1);
    }

    private static final class FakeTokenStream implements TokenStream {
        private final boolean fail;
        private final boolean emitTokens;
        private final String completionText;
        private final AtomicInteger starts = new AtomicInteger();
        private Consumer<String> onNext = ignored -> {};
        private Consumer<Response<AiMessage>> onComplete = ignored -> {};
        private Consumer<Throwable> onError = ignored -> {};

        private FakeTokenStream(boolean fail, boolean emitTokens) {
            this(fail, emitTokens, "hello world");
        }

        private FakeTokenStream(boolean fail, boolean emitTokens, String completionText) {
            this.fail = fail;
            this.emitTokens = emitTokens;
            this.completionText = completionText;
        }

        @Override public TokenStream onRetrieved(Consumer<List<Content>> consumer) { return this; }
        @Override public TokenStream onNext(Consumer<String> consumer) { this.onNext = consumer; return this; }
        @Override public TokenStream onComplete(Consumer<Response<AiMessage>> consumer) { this.onComplete = consumer; return this; }
        @Override public TokenStream onError(Consumer<Throwable> consumer) { this.onError = consumer; return this; }
        @Override public TokenStream ignoreErrors() { return this; }

        @Override
        public void start() {
            starts.incrementAndGet();
            if (fail) {
                onError.accept(new IllegalStateException("boom"));
                return;
            }
            if (emitTokens) {
                onNext.accept("hello ");
                onNext.accept("world");
            }
            onComplete.accept(Response.from(AiMessage.from(completionText)));
        }
    }

    private static final class IntermediateCompletionTokenStream implements TokenStream {
        private Consumer<Response<AiMessage>> onComplete = ignored -> {};
        private Consumer<Throwable> onError = ignored -> {};

        @Override public TokenStream onRetrieved(Consumer<List<Content>> consumer) { return this; }
        @Override public TokenStream onNext(Consumer<String> consumer) { return this; }
        @Override public TokenStream onComplete(Consumer<Response<AiMessage>> consumer) { this.onComplete = consumer; return this; }
        @Override public TokenStream onError(Consumer<Throwable> consumer) { this.onError = consumer; return this; }
        @Override public TokenStream ignoreErrors() { return this; }

        @Override
        public void start() {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1").name("whereAmI").arguments("{}").build();
            onComplete.accept(Response.from(AiMessage.from("planning", List.of(request))));
            onComplete.accept(Response.from(AiMessage.from("final answer")));
        }
    }

    private static final class ManualTokenStream implements TokenStream {
        private final CountDownLatch started = new CountDownLatch(1);
        private Consumer<Response<AiMessage>> onComplete = ignored -> {};
        private Consumer<Throwable> onError = ignored -> {};

        @Override public TokenStream onRetrieved(Consumer<List<Content>> consumer) { return this; }
        @Override public TokenStream onNext(Consumer<String> consumer) { return this; }
        @Override public TokenStream onComplete(Consumer<Response<AiMessage>> consumer) { this.onComplete = consumer; return this; }
        @Override public TokenStream onError(Consumer<Throwable> consumer) { this.onError = consumer; return this; }
        @Override public TokenStream ignoreErrors() { return this; }
        @Override public void start() { started.countDown(); }

        private void complete(String text) {
            onComplete.accept(Response.from(AiMessage.from(text)));
        }
    }
}
