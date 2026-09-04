package xin.claw.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ItemPickupListenerTest {
    @Test
    void batchesBotPickupsIntoOneAgentEventAndIgnoresOtherCollectors() {
        List<Runnable> asyncTasks = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        ItemPickupListener listener = new ItemPickupListener(
            () -> 42,
            entityId -> entityId == 7 ? "minecraft:diamond" : "unknown_item_entity_" + entityId,
            asyncTasks::add,
            messages::add,
            0L
        );

        listener.handlePickup(99, 7, 4);
        listener.handlePickup(42, 7, 2);
        listener.handlePickup(42, 8, 3);

        assertEquals(1, asyncTasks.size(), "one debounce task should cover the pickup burst");
        asyncTasks.get(0).run();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("minecraft:diamond x2"), messages.get(0));
        assertTrue(messages.get(0).contains("unknown_item_entity_8 x3"), messages.get(0));
        assertTrue(messages.get(0).startsWith("[SYSTEM_EVENT]"), messages.get(0));
    }
}
