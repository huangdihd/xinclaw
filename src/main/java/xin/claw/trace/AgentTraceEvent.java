package xin.claw.trace;

import com.google.gson.JsonObject;

/** One immutable, ordered XinClaw runtime event. */
public record AgentTraceEvent(
    long sequence,
    long monotonicNanos,
    String eventType,
    JsonObject payload
) {
    public AgentTraceEvent {
        if (sequence < 1) throw new IllegalArgumentException("sequence must be positive");
        if (monotonicNanos < 0) throw new IllegalArgumentException("monotonicNanos must be non-negative");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        payload = payload == null ? new JsonObject() : payload.deepCopy();
    }

    @Override
    public JsonObject payload() {
        return payload.deepCopy();
    }
}
