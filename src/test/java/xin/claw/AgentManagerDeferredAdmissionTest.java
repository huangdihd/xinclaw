package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xin.claw.tasks.TaskManager;

final class AgentManagerDeferredAdmissionTest {
    @TempDir
    Path tempDir;
    private ExecutorService executor;

    @AfterEach
    void cleanup() throws Exception {
        XinClawPlugin.INSTANCE = null;
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void pendingUserSubmissionWinsRaceAndDeferredMessageIsNotInterruptedOrLost() throws Exception {
        CountDownLatch releaseExecutor = new CountDownLatch(1);
        CountDownLatch blockerStarted = new CountDownLatch(2);
        executor = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                blockerStarted.countDown();
                await(releaseExecutor);
            });
        }
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

        RecordingAgent agent = new RecordingAgent();
        AgentManager manager = new AgentManager(
            agent, new TaskManager(tempDir.resolve("tasks.json").toFile()));
        XinClawPlugin plugin = new XinClawPlugin();
        plugin.executorService = executor;
        plugin.agentManager = manager;
        XinClawPlugin.INSTANCE = plugin;

        AtomicInteger interrupts = new AtomicInteger();
        AtomicInteger drains = new AtomicInteger();
        AtomicBoolean pickupPending = new AtomicBoolean(true);
        AtomicBoolean pickupAccepted = new AtomicBoolean();
        CountDownLatch pickupCheckFinished = new CountDownLatch(1);
        manager.setDeferredProcessingCheck(() -> {
            if (!pickupPending.get()) return;
            try {
                pickupAccepted.set(manager.tryProcessDeferredMessage(() -> {
                    drains.incrementAndGet();
                    pickupPending.set(false);
                    return "[SYSTEM_EVENT] pickup";
                }, ignored -> {}));
            } finally {
                pickupCheckFinished.countDown();
            }
        });

        manager.submitMessage("user command", interrupts::incrementAndGet, ignored -> {});

        assertFalse(manager.tryProcessDeferredMessage(() -> {
            drains.incrementAndGet();
            return "[SYSTEM_EVENT] pickup";
        }, ignored -> {}));
        assertEquals(0, drains.get(), "failed admission must leave pickup accumulation untouched");

        releaseExecutor.countDown();
        assertTrue(agent.twoCalls.await(3, TimeUnit.SECONDS));
        assertTrue(pickupCheckFinished.await(3, TimeUnit.SECONDS));

        assertTrue(pickupAccepted.get());
        assertEquals(List.of("user command", "[SYSTEM_EVENT] pickup"), agent.messages);
        assertEquals(1, drains.get());
        assertEquals(0, interrupts.get(), "deferred notification must not cause user interruption");
        assertEquals(1, agent.maxConcurrent.get(), "conversations must never overlap");
    }

    @Test
    void errorCleanupReleasesSlotBeforeSchedulingDeferredCheck() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        AgentManager manager = manager(message -> new FailingTokenStream());
        installPlugin(manager);
        CountDownLatch checked = new CountDownLatch(1);
        AtomicBoolean slotWasFree = new AtomicBoolean();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        manager.setDeferredProcessingCheck(() -> {
            callbackThread.set(Thread.currentThread());
            slotWasFree.set(!manager.isProcessing());
            checked.countDown();
        });

        Thread processingThread = Thread.currentThread();
        String response = manager.processMessage("hello");

        assertTrue(response.startsWith("Agent error:"), response);
        assertTrue(checked.await(2, TimeUnit.SECONDS));
        assertTrue(slotWasFree.get());
        assertNotSame(processingThread, callbackThread.get(), "cleanup check must be enqueued, never recursive");
    }

    @Test
    void interruptedConversationSchedulesCheckOnlyAfterTerminalCleanup() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        ManualTokenStream stream = new ManualTokenStream();
        AgentManager manager = manager(message -> stream);
        installPlugin(manager);
        CountDownLatch checked = new CountDownLatch(1);
        AtomicBoolean slotWasFree = new AtomicBoolean();
        manager.setDeferredProcessingCheck(() -> {
            slotWasFree.set(!manager.isProcessing());
            checked.countDown();
        });
        AtomicReference<String> response = new AtomicReference<>();
        Thread worker = new Thread(() -> response.set(manager.processMessage("hello")));
        worker.start();
        assertTrue(stream.started.await(2, TimeUnit.SECONDS));

        worker.interrupt();
        assertFalse(checked.await(100, TimeUnit.MILLISECONDS));
        assertTrue(manager.isProcessing(), "interruption must not release admission before stream terminal callback");

        stream.complete();
        worker.join(2000);
        assertTrue(checked.await(2, TimeUnit.SECONDS));
        assertEquals("", response.get());
        assertTrue(slotWasFree.get());
    }

    @Test
    void failedInterruptCallbackDoesNotLeaveAUserReservationBlockingPickups() throws Exception {
        executor = Executors.newSingleThreadExecutor();
        ManualTokenStream stream = new ManualTokenStream();
        AgentManager manager = manager(message -> stream);
        installPlugin(manager);
        CountDownLatch callbackFailed = new CountDownLatch(1);
        Thread worker = new Thread(() -> manager.processMessage("hello"));
        worker.start();
        assertTrue(stream.started.await(2, TimeUnit.SECONDS));
        manager.submitMessage("next user", () -> {
            callbackFailed.countDown();
            throw new IllegalStateException("chat transport unavailable");
        }, ignored -> {});
        assertTrue(callbackFailed.await(2, TimeUnit.SECONDS));
        executor.submit(() -> {}).get(2, TimeUnit.SECONDS);
        stream.complete();
        worker.join(2000);
        AtomicBoolean drained = new AtomicBoolean();
        manager.tryProcessDeferredMessage(() -> {
            drained.set(true);
            return null;
        }, ignored -> {});
        assertTrue(drained.get(), "failed user submission must release its priority reservation");
    }

    @Test
    void cancelBeforeAdmissionSchedulesAnotherCheckForPreservedPickups() {
        QueuedExecutor queued = new QueuedExecutor();
        executor = queued;
        AgentManager manager = manager(message -> new CompletingTokenStream(() -> {}));
        installPlugin(manager);
        AtomicBoolean drained = new AtomicBoolean();
        manager.setDeferredProcessingCheck(() -> manager.tryProcessDeferredMessage(() -> {
            drained.set(true);
            return "[SYSTEM_EVENT] pickup";
        }, ignored -> {}));
        manager.submitMessage("queued user", null, ignored -> {});
        queued.runNext(); // outer submission reserves and queues the actual conversation
        assertFalse(manager.tryProcessDeferredMessage(() -> {
            drained.set(true);
            return "[SYSTEM_EVENT] pickup";
        }, ignored -> {}));
        assertFalse(drained.get());
        assertNull(manager.requestInterruptProcessing(), "user has not acquired the processing slot");
        queued.runNext(); // canceled user future
        assertFalse(drained.get(), "check must be scheduled rather than called recursively");
        assertFalse(queued.tasks.isEmpty(), "canceling the pending user must wake deferred pickups");
        queued.runNext();
        assertTrue(drained.get());
    }

    private static final class QueuedExecutor extends java.util.concurrent.AbstractExecutorService {
        private final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();
        private boolean shutdown;
        @Override public void execute(Runnable task) { tasks.add(task); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> remaining = new ArrayList<>(tasks);
            tasks.clear();
            return remaining;
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        private void runNext() { tasks.removeFirst().run(); }
    }

    private AgentManager manager(AgentManager.BotAgent agent) {
        return new AgentManager(agent, new TaskManager(tempDir.resolve("tasks.json").toFile()));
    }

    private void installPlugin(AgentManager manager) {
        XinClawPlugin plugin = new XinClawPlugin();
        plugin.executorService = executor;
        plugin.agentManager = manager;
        XinClawPlugin.INSTANCE = plugin;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RecordingAgent implements AgentManager.BotAgent {
        private final List<String> messages = java.util.Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final CountDownLatch twoCalls = new CountDownLatch(2);

        @Override
        public TokenStream chat(String message) {
            messages.add(message);
            int now = active.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            return new CompletingTokenStream(() -> {
                active.decrementAndGet();
                twoCalls.countDown();
            });
        }
    }

    private static final class CompletingTokenStream implements TokenStream {
        private final Runnable completed;
        private Consumer<Response<AiMessage>> onComplete = ignored -> {};

        private CompletingTokenStream(Runnable completed) {
            this.completed = completed;
        }

        @Override public TokenStream onRetrieved(Consumer<List<Content>> consumer) { return this; }
        @Override public TokenStream onNext(Consumer<String> consumer) { return this; }
        @Override public TokenStream onComplete(Consumer<Response<AiMessage>> consumer) {
            this.onComplete = consumer;
            return this;
        }
        @Override public TokenStream onError(Consumer<Throwable> consumer) { return this; }
        @Override public TokenStream ignoreErrors() { return this; }

        @Override
        public void start() {
            completed.run();
            onComplete.accept(Response.from(AiMessage.from("done")));
        }
    }

    private static final class FailingTokenStream implements TokenStream {
        private Consumer<Throwable> onError = ignored -> {};

        @Override public TokenStream onRetrieved(Consumer<List<Content>> consumer) { return this; }
        @Override public TokenStream onNext(Consumer<String> consumer) { return this; }
        @Override public TokenStream onComplete(Consumer<Response<AiMessage>> consumer) { return this; }
        @Override public TokenStream onError(Consumer<Throwable> consumer) { this.onError = consumer; return this; }
        @Override public TokenStream ignoreErrors() { return this; }
        @Override public void start() { onError.accept(new IllegalStateException("boom")); }
    }

    private static final class ManualTokenStream implements TokenStream {
        private final CountDownLatch started = new CountDownLatch(1);
        private Consumer<Response<AiMessage>> onComplete = ignored -> {};

        @Override public TokenStream onRetrieved(Consumer<List<Content>> consumer) { return this; }
        @Override public TokenStream onNext(Consumer<String> consumer) { return this; }
        @Override public TokenStream onComplete(Consumer<Response<AiMessage>> consumer) {
            this.onComplete = consumer;
            return this;
        }
        @Override public TokenStream onError(Consumer<Throwable> consumer) { return this; }
        @Override public TokenStream ignoreErrors() { return this; }
        @Override public void start() { started.countDown(); }
        private void complete() { onComplete.accept(Response.from(AiMessage.from("done"))); }
    }
}
