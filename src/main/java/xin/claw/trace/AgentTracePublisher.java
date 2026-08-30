package xin.claw.trace;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Process-local trace bus used by benchmark recorders and diagnostics. */
public final class AgentTracePublisher {
    private static final Logger logger = LoggerFactory.getLogger(AgentTracePublisher.class);
    private final CopyOnWriteArrayList<AgentTraceListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong nextSequence = new AtomicLong(1L);
    private final LongSupplier monotonicClock;

    public AgentTracePublisher() {
        this(System::nanoTime);
    }

    public AgentTracePublisher(LongSupplier monotonicClock) {
        this.monotonicClock = Objects.requireNonNull(monotonicClock, "monotonicClock");
    }

    public AutoCloseable subscribe(AgentTraceListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public AgentTraceEvent emit(String eventType, JsonObject payload) {
        AgentTraceEvent event = new AgentTraceEvent(
            nextSequence.getAndIncrement(), monotonicClock.getAsLong(), eventType, payload
        );
        for (AgentTraceListener listener : listeners) {
            try {
                listener.onTraceEvent(event);
            } catch (RuntimeException error) {
                logger.warn("XinClaw trace listener rejected event {}", eventType, error);
            }
        }
        return event;
    }
}
