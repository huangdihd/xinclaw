package xin.claw;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ProcessingAdmissionGateTest {
    @Test
    void cleanupAndSlotAdmissionCannotCross() throws Exception {
        ProcessingAdmissionGate gate = new ProcessingAdmissionGate();
        AtomicReference<Thread> slot = new AtomicReference<>();
        AtomicBoolean allowed = new AtomicBoolean(true);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicReference<ProcessingAdmissionGate.Result> result = new AtomicReference<>();

        Thread cleanup = new Thread(() -> gate.blockAdmissions(() -> {
            allowed.set(false);
            cleanupEntered.countDown();
            try {
                releaseCleanup.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }));
        cleanup.start();
        assertTrue(cleanupEntered.await(2, TimeUnit.SECONDS));

        Thread candidate = new Thread(() -> result.set(
            gate.tryAcquire(slot, Thread.currentThread(), allowed::get)));
        candidate.start();
        Thread.sleep(50);
        assertTrue(candidate.isAlive(), "admission must wait while cleanup owns the gate");
        assertNull(slot.get());

        releaseCleanup.countDown();
        cleanup.join(2000);
        candidate.join(2000);

        assertEquals(ProcessingAdmissionGate.Result.REJECTED, result.get());
        assertNull(slot.get());
    }

    @Test
    void admittedCallerAtomicallyOccupiesSlot() {
        ProcessingAdmissionGate gate = new ProcessingAdmissionGate();
        AtomicReference<Thread> slot = new AtomicReference<>();
        Thread current = Thread.currentThread();

        assertEquals(
            ProcessingAdmissionGate.Result.ACQUIRED,
            gate.tryAcquire(slot, current, () -> true));
        assertSame(current, slot.get());
        assertEquals(
            ProcessingAdmissionGate.Result.BUSY,
            gate.tryAcquire(slot, new Thread(), () -> true));
    }

    @Test
    void reservedUserAdmissionWinsOverDeferredDrainWithoutLosingIt() {
        ProcessingAdmissionGate gate = new ProcessingAdmissionGate();
        AtomicReference<Thread> slot = new AtomicReference<>();
        AtomicInteger drains = new AtomicInteger();
        ProcessingAdmissionGate.PriorityAdmission user = gate.reservePriorityAdmission();
        Thread notification = new Thread();

        assertEquals(
            ProcessingAdmissionGate.Result.BUSY,
            gate.tryAcquireDeferred(slot, notification, () -> true, drains::incrementAndGet));
        assertNull(slot.get(), "deferred work must not run in parallel with a pending user message");
        assertEquals(0, drains.get(), "failed admission must preserve the undrained notification");

        Thread userThread = Thread.currentThread();
        assertEquals(
            ProcessingAdmissionGate.Result.ACQUIRED,
            gate.tryAcquirePriority(slot, userThread, () -> true, user));
        assertSame(userThread, slot.get());
        assertFalse(userThread.isInterrupted(), "notification admission must never interrupt user processing");

        slot.set(null);
        assertEquals(
            ProcessingAdmissionGate.Result.ACQUIRED,
            gate.tryAcquireDeferred(slot, notification, () -> true, () -> {
                assertSame(notification, slot.get(), "slot must be held before notification state drains");
                drains.incrementAndGet();
            }));
        assertEquals(1, drains.get());
    }

    @Test
    void cancellingAReservedUserAdmissionUnblocksDeferredWork() {
        ProcessingAdmissionGate gate = new ProcessingAdmissionGate();
        AtomicReference<Thread> slot = new AtomicReference<>();
        ProcessingAdmissionGate.PriorityAdmission user = gate.reservePriorityAdmission();
        gate.cancelPriorityAdmission(user);

        assertEquals(
            ProcessingAdmissionGate.Result.ACQUIRED,
            gate.tryAcquireDeferred(slot, Thread.currentThread(), () -> true, () -> {}));
    }
}
