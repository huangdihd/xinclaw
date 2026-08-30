package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.AiMessage;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

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
    void glmCodingPlanThinkingUsesReasoningTraceAdapter() throws Exception {
        String originalModel = PluginConfig.modelName;
        boolean originalThinking = PluginConfig.enableThinking;
        try {
            PluginConfig.modelName = "glm-coding-plan/glm-5.3-flash";
            PluginConfig.enableThinking = true;

            SingleTerminalStreamingChatLanguageModel model = assertInstanceOf(
                SingleTerminalStreamingChatLanguageModel.class,
                AgentManager.buildStreamingModel()
            );
            Field delegate = SingleTerminalStreamingChatLanguageModel.class.getDeclaredField("delegate");
            delegate.setAccessible(true);

            assertInstanceOf(DeepSeekThinkingStreamingChatLanguageModel.class, delegate.get(model));
        } finally {
            PluginConfig.modelName = originalModel;
            PluginConfig.enableThinking = originalThinking;
        }
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
    void retriesToollessFutureCommitmentUntilAToolIsActuallyRequested() {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicLong completedTools = new java.util.concurrent.atomic.AtomicLong();
        List<String> inputs = new ArrayList<>();
        AgentManager.BotAgent agent = message -> {
            inputs.add(message);
            if (calls.getAndIncrement() == 0) {
                return new FakeTokenStream(false, false, "I'll start by checking my surroundings.");
            }
            completedTools.incrementAndGet();
            return new FakeTokenStream(false, false, "final answer");
        };

        String response = AgentManager.executeWithActionGuard(
            agent,
            "Find the nearby pillager outpost and enter it.",
            completedTools::get
        );

        assertEquals("final answer", response);
        assertEquals(2, calls.get());
        assertTrue(inputs.get(1).contains("[ACTION_CORRECTION]"));
        assertTrue(inputs.get(1).contains("Find the nearby pillager outpost and enter it."));
    }

    @Test
    void retriesToollessPlanningPreambleObservedInBenchmark() {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicLong completedTools = new java.util.concurrent.atomic.AtomicLong();
        AgentManager.BotAgent agent = message -> {
            if (calls.getAndIncrement() == 0) {
                return new FakeTokenStream(
                    false,
                    false,
                    "I'll break this into a task list, locate the outpost, navigate to it, then enter it."
                );
            }
            completedTools.incrementAndGet();
            return new FakeTokenStream(false, false, "final answer");
        };

        String response = AgentManager.executeWithActionGuard(
            agent,
            "Find and enter the outpost.",
            completedTools::get
        );

        assertEquals("final answer", response);
        assertEquals(2, calls.get());
    }

    @Test
    void rejectsARepeatedToollessFutureCommitment() {
        AtomicInteger calls = new AtomicInteger();
        AgentManager.BotAgent agent = message -> {
            calls.incrementAndGet();
            return new FakeTokenStream(false, false, "I'll start later.");
        };

        String response = AgentManager.executeWithActionGuard(agent, "Find the outpost.", () -> 0L);

        assertEquals("Agent failed to begin execution: no tool was requested.", response);
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryConversationalLetMeResponse() {
        AtomicInteger calls = new AtomicInteger();
        AgentManager.BotAgent agent = message -> {
            calls.incrementAndGet();
            return new FakeTokenStream(false, false, "Let me explain how pathfinding works.");
        };

        String response = AgentManager.executeWithActionGuard(
            agent,
            "Explain how pathfinding works.",
            () -> 0L
        );

        assertEquals("Let me explain how pathfinding works.", response);
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotCoerceToolsForExplanatoryQuestionContainingActionVerb() {
        AtomicInteger calls = new AtomicInteger();
        AgentManager.BotAgent agent = message -> {
            calls.incrementAndGet();
            return new FakeTokenStream(false, false, "I'll start by explaining how to find diamonds.");
        };

        String response = AgentManager.executeWithActionGuard(
            agent,
            "Can you explain how to find diamonds?",
            () -> 0L
        );

        assertEquals("I'll start by explaining how to find diamonds.", response);
        assertEquals(1, calls.get());
    }

    @Test
    void retriesExecutableLetMeActionPreamble() {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicLong completedTools = new java.util.concurrent.atomic.AtomicLong();
        AgentManager.BotAgent agent = message -> {
            if (calls.getAndIncrement() == 0) {
                return new FakeTokenStream(false, false, "Let me check my surroundings.");
            }
            completedTools.incrementAndGet();
            return new FakeTokenStream(false, false, "checked");
        };

        String response = AgentManager.executeWithActionGuard(
            agent,
            "Check my surroundings.",
            completedTools::get
        );

        assertEquals("checked", response);
        assertEquals(2, calls.get());
    }

    @Test
    void classifiesCommonDirectToolRequestsButNotExplanations() {
        for (String request : List.of(
            "show my inventory",
            "list my tasks",
            "get my vitals",
            "stop walking",
            "use the item",
            "Can you find the outpost?",
            "请你寻找前哨站",
            "帮我找前哨站"
        )) {
            assertTrue(AgentManager.requiresToolAction(request), request);
        }
        for (String request : List.of(
            "Can you explain how to find diamonds?",
            "Explain how pathfinding works.",
            "What does scanSurroundings do?",
            "Tell me how to build a house."
        )) {
            assertFalse(AgentManager.requiresToolAction(request), request);
        }
    }

    @Test
    void doesNotRetryToolUsingTurnWhoseFinalTextLooksLikeCommitment() {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicLong completedTools = new java.util.concurrent.atomic.AtomicLong();
        AgentManager.BotAgent agent = message -> {
            calls.incrementAndGet();
            completedTools.incrementAndGet();
            return new FakeTokenStream(false, false, "I'll start moving now.");
        };

        String response = AgentManager.executeWithActionGuard(
            agent,
            "Find and enter the outpost.",
            completedTools::get
        );

        assertEquals("I'll start moving now.", response);
        assertEquals(1, calls.get());
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
