package xin.claw.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AgentTracePublisherTest {
    @Test
    void publishesOrderedImmutableEventsAndStopsAfterUnsubscribe() {
        AgentTracePublisher publisher = new AgentTracePublisher(() -> 1234L);
        List<AgentTraceEvent> received = new ArrayList<>();
        AutoCloseable subscription = publisher.subscribe(received::add);

        JsonObject payload = new JsonObject();
        payload.addProperty("text", "first");
        publisher.emit("agent_input", payload);
        payload.addProperty("text", "mutated later");
        publisher.emit("agent_output", new JsonObject());
        close(subscription);
        publisher.emit("ignored", new JsonObject());

        assertEquals(2, received.size());
        assertEquals(1L, received.get(0).sequence());
        assertEquals(2L, received.get(1).sequence());
        assertEquals(1234L, received.get(0).monotonicNanos());
        assertEquals("agent_input", received.get(0).eventType());
        assertEquals("first", received.get(0).payload().get("text").getAsString());
    }

    private static void close(AutoCloseable subscription) {
        try {
            subscription.close();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
