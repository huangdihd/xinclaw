package xin.claw;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class ProcessingAdmissionGate {
    enum Result { ACQUIRED, REJECTED, BUSY }

    private final Object lock = new Object();

    Result tryAcquire(
            AtomicReference<Thread> slot,
            Thread candidate,
            BooleanSupplier admissionAllowed) {
        synchronized (lock) {
            if (!admissionAllowed.getAsBoolean()) return Result.REJECTED;
            return slot.compareAndSet(null, candidate) ? Result.ACQUIRED : Result.BUSY;
        }
    }

    void blockAdmissions(Runnable cleanup) {
        synchronized (lock) {
            cleanup.run();
        }
    }

    <T> T underGate(Supplier<T> action) {
        synchronized (lock) {
            return action.get();
        }
    }
}
