package xin.claw;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class ProcessingAdmissionGate {
    enum Result { ACQUIRED, REJECTED, BUSY }

    static final class PriorityAdmission {
        private final ProcessingAdmissionGate owner;
        private boolean pending = true;

        private PriorityAdmission(ProcessingAdmissionGate owner) {
            this.owner = owner;
        }
    }

    private final Object lock = new Object();
    private int pendingPriorityAdmissions;

    PriorityAdmission reservePriorityAdmission() {
        synchronized (lock) {
            pendingPriorityAdmissions++;
            return new PriorityAdmission(this);
        }
    }

    void cancelPriorityAdmission(PriorityAdmission admission) {
        synchronized (lock) {
            consumePriorityAdmission(admission);
        }
    }

    Result tryAcquire(
            AtomicReference<Thread> slot,
            Thread candidate,
            BooleanSupplier admissionAllowed) {
        synchronized (lock) {
            if (!admissionAllowed.getAsBoolean()) return Result.REJECTED;
            if (pendingPriorityAdmissions > 0) return Result.BUSY;
            return slot.compareAndSet(null, candidate) ? Result.ACQUIRED : Result.BUSY;
        }
    }

    Result tryAcquirePriority(
            AtomicReference<Thread> slot,
            Thread candidate,
            BooleanSupplier admissionAllowed,
            PriorityAdmission admission) {
        synchronized (lock) {
            consumePriorityAdmission(admission);
            if (!admissionAllowed.getAsBoolean()) return Result.REJECTED;
            return slot.compareAndSet(null, candidate) ? Result.ACQUIRED : Result.BUSY;
        }
    }

    Result tryAcquireDeferred(
            AtomicReference<Thread> slot,
            Thread candidate,
            BooleanSupplier admissionAllowed,
            Runnable afterAcquired) {
        synchronized (lock) {
            if (!admissionAllowed.getAsBoolean()) return Result.REJECTED;
            if (pendingPriorityAdmissions > 0 || !slot.compareAndSet(null, candidate)) {
                return Result.BUSY;
            }
            try {
                afterAcquired.run();
                return Result.ACQUIRED;
            } catch (RuntimeException | Error failure) {
                slot.compareAndSet(candidate, null);
                throw failure;
            }
        }
    }

    private void consumePriorityAdmission(PriorityAdmission admission) {
        if (admission == null || admission.owner != this) {
            throw new IllegalArgumentException("priority admission belongs to another gate");
        }
        if (!admission.pending) return;
        admission.pending = false;
        pendingPriorityAdmissions--;
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
