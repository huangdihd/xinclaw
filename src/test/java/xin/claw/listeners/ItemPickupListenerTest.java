package xin.claw.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import xin.bbtt.mcbot.events.DisconnectEvent;

final class ItemPickupListenerTest {
    @Test
    void batchesBotPickupsIntoOneAgentEventAndIgnoresOtherCollectors() {
        List<Runnable> asyncTasks = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        ItemPickupListener listener = listener(asyncTasks, messages, () -> false);

        listener.handlePickup(99, 7, 4);
        listener.handlePickup(42, 7, 2);
        listener.handlePickup(42, 8, 3);

        assertEquals(1, asyncTasks.size(), "one debounce task should cover the pickup burst");
        asyncTasks.remove(0).run();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("minecraft:diamond x2"), messages.get(0));
        assertTrue(messages.get(0).contains("unknown_item_entity_8 x3"), messages.get(0));
        assertTrue(messages.get(0).startsWith("[SYSTEM_EVENT]"), messages.get(0));
    }

    @Test
    void busyPickupsWaitForConversationCleanupThenProduceOneAggregate() {
        List<Runnable> asyncTasks = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        AtomicBoolean busy = new AtomicBoolean(true);
        ItemPickupListener listener = listener(asyncTasks, messages, busy::get);

        listener.handlePickup(42, 7, 2);
        listener.handlePickup(42, 7, 3);
        listener.handlePickup(42, 8, 1);

        assertTrue(asyncTasks.isEmpty(), "busy pickup bursts must not periodically submit work");
        assertTrue(messages.isEmpty(), "pickup must not interrupt the active conversation");

        busy.set(false);
        listener.onProcessingAvailable();

        assertEquals(1, asyncTasks.size());
        asyncTasks.remove(0).run();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("minecraft:diamond x5"), messages.get(0));
        assertTrue(messages.get(0).contains("unknown_item_entity_8 x1"), messages.get(0));
    }

    @Test
    void pickupsArrivingDuringNotificationProcessingEnterTheNextBatch() {
        List<Runnable> asyncTasks = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        AtomicBoolean busy = new AtomicBoolean(false);
        AtomicBoolean firstNotification = new AtomicBoolean(true);
        ItemPickupListener[] holder = new ItemPickupListener[1];
        holder[0] = new ItemPickupListener(
            () -> 42,
            entityId -> entityId == 7 ? "minecraft:diamond" : "minecraft:iron_ingot",
            asyncTasks::add,
            busy::get,
            (message, onReply) -> {
                busy.set(true);
                messages.add(message.get());
                if (firstNotification.getAndSet(false)) {
                    holder[0].handlePickup(42, 8, 4);
                }
                return true;
            },
            0L
        );

        holder[0].handlePickup(42, 7, 1);
        asyncTasks.remove(0).run();

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("minecraft:diamond x1"), messages.get(0));
        assertTrue(asyncTasks.isEmpty(), "processing arrivals must wait for lifecycle cleanup");

        busy.set(false);
        holder[0].onProcessingAvailable();
        asyncTasks.remove(0).run();

        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("minecraft:iron_ingot x4"), messages.get(1));
        assertTrue(!messages.get(1).contains("minecraft:diamond"), messages.get(1));
    }

    @Test
    void clearingPendingInvalidatesScheduledDebounceWork() {
        List<Runnable> asyncTasks = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        ItemPickupListener listener = listener(asyncTasks, messages, () -> false);

        listener.handlePickup(42, 7, 2);
        assertEquals(1, asyncTasks.size());
        listener.onDisconnect(new DisconnectEvent(Component.text("test disconnect")));
        asyncTasks.remove(0).run();
        assertTrue(messages.isEmpty(), "stale scheduled work must not survive disconnect/unload");

        listener.handlePickup(42, 8, 1);
        assertEquals(1, asyncTasks.size());
        asyncTasks.remove(0).run();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("unknown_item_entity_8 x1"), messages.get(0));
        assertTrue(!messages.get(0).contains("minecraft:diamond"), messages.get(0));
    }

    @Test
    void failedAdmissionPreservesAccumulationForTheNextCleanupCheck() {
        List<Runnable> asyncTasks = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        AtomicBoolean accept = new AtomicBoolean(false);
        ItemPickupListener listener = new ItemPickupListener(
            () -> 42,
            entityId -> "minecraft:diamond",
            asyncTasks::add,
            () -> false,
            (message, onReply) -> {
                if (!accept.get()) return false;
                messages.add(message.get());
                return true;
            },
            0L
        );

        listener.handlePickup(42, 7, 3);
        asyncTasks.remove(0).run();
        assertTrue(messages.isEmpty());

        accept.set(true);
        listener.onProcessingAvailable();
        asyncTasks.remove(0).run();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("minecraft:diamond x3"), messages.get(0));
    }

    private static ItemPickupListener listener(
            List<Runnable> asyncTasks,
            List<String> messages,
            java.util.function.BooleanSupplier busy) {
        return new ItemPickupListener(
            () -> 42,
            entityId -> entityId == 7 ? "minecraft:diamond" : "unknown_item_entity_" + entityId,
            asyncTasks::add,
            busy,
            (message, onReply) -> {
                messages.add(message.get());
                return true;
            },
            0L
        );
    }
}
