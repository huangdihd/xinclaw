package xin.claw.listeners;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTakeItemEntityPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.MovementSync;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.DisablePluginEvent;
import xin.bbtt.mcbot.events.DisconnectEvent;
import xin.bbtt.mcbot.events.ReceivePacketEvent;
import xin.claw.XinClawPlugin;
import xin.claw.utils.ItemStateParser;

public final class ItemPickupListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(ItemPickupListener.class);
    private static final long DEFAULT_BATCH_MILLIS = 500L;
    private final IntSupplier botEntityId;
    private final IntFunction<String> itemNameResolver;
    private final Consumer<Runnable> asyncSubmit;
    private final BooleanSupplier agentBusy;
    private final DeferredAgentNotify agentNotify;
    private final long batchMillis;
    private final Object lock = new Object();
    private final LinkedHashMap<String, Integer> pending = new LinkedHashMap<>();
    private boolean flushScheduled;
    private long generation;

    @FunctionalInterface
    interface DeferredAgentNotify {
        boolean tryNotify(Supplier<String> message, Consumer<String> onReply);
    }

    public ItemPickupListener() {
        this(
            () -> MovementSync.INSTANCE == null ? -1 : MovementSync.INSTANCE.entityId,
            ItemPickupListener::resolveItemName,
            task -> {
                XinClawPlugin plugin = XinClawPlugin.INSTANCE;
                if (plugin != null && plugin.executorService != null && !plugin.executorService.isShutdown()) {
                    plugin.executorService.submit(task);
                }
            },
            () -> {
                XinClawPlugin plugin = XinClawPlugin.INSTANCE;
                return plugin != null && plugin.agentManager != null && plugin.agentManager.isProcessing();
            },
            (message, onReply) -> {
                XinClawPlugin plugin = XinClawPlugin.INSTANCE;
                if (plugin != null && plugin.agentManager != null) {
                    return plugin.agentManager.tryProcessDeferredMessage(message, onReply);
                }
                return false;
            },
            DEFAULT_BATCH_MILLIS
        );
    }

    ItemPickupListener(
            IntSupplier botEntityId,
            IntFunction<String> itemNameResolver,
            Consumer<Runnable> asyncSubmit,
            BooleanSupplier agentBusy,
            DeferredAgentNotify agentNotify,
            long batchMillis) {
        this.botEntityId = Objects.requireNonNull(botEntityId, "botEntityId");
        this.itemNameResolver = Objects.requireNonNull(itemNameResolver, "itemNameResolver");
        this.asyncSubmit = Objects.requireNonNull(asyncSubmit, "asyncSubmit");
        this.agentBusy = Objects.requireNonNull(agentBusy, "agentBusy");
        this.agentNotify = Objects.requireNonNull(agentNotify, "agentNotify");
        if (batchMillis < 0) throw new IllegalArgumentException("batchMillis must be non-negative");
        this.batchMillis = batchMillis;
    }

    @EventHandler
    public void onItemPickup(ReceivePacketEvent<ClientboundTakeItemEntityPacket> event) {
        ClientboundTakeItemEntityPacket packet = event.getPacket();
        handlePickup(
            packet.getCollectorEntityId(),
            packet.getCollectedEntityId(),
            packet.getItemCount()
        );
    }

    @EventHandler
    public void onDisconnect(DisconnectEvent event) {
        clearPending();
    }

    @EventHandler
    public void onPluginDisable(DisablePluginEvent event) {
        if (event.getPlugin() == XinClawPlugin.INSTANCE) clearPending();
    }

    void handlePickup(int collectorEntityId, int collectedEntityId, int itemCount) {
        if (collectorEntityId != botEntityId.getAsInt() || itemCount <= 0) return;
        String itemName = itemNameResolver.apply(collectedEntityId);
        if (itemName == null || itemName.isBlank()) itemName = "unknown_item_entity_" + collectedEntityId;
        long scheduledGeneration;
        synchronized (lock) {
            pending.merge(itemName, itemCount, Integer::sum);
            if (flushScheduled || agentBusy.getAsBoolean()) return;
            flushScheduled = true;
            scheduledGeneration = generation;
        }
        scheduleFlush(true, scheduledGeneration);
    }

    public void onProcessingAvailable() {
        long scheduledGeneration;
        synchronized (lock) {
            if (pending.isEmpty() || flushScheduled || agentBusy.getAsBoolean()) return;
            flushScheduled = true;
            scheduledGeneration = generation;
        }
        scheduleFlush(false, scheduledGeneration);
    }

    public void clearPending() {
        synchronized (lock) {
            pending.clear();
            flushScheduled = false;
            generation++;
        }
    }

    private void scheduleFlush(boolean debounce, long scheduledGeneration) {
        try {
            asyncSubmit.accept(() -> flush(debounce, scheduledGeneration));
        } catch (RuntimeException error) {
            synchronized (lock) {
                if (generation == scheduledGeneration) flushScheduled = false;
            }
            logger.warn("Failed to schedule item-pickup Agent notification", error);
        }
    }

    private void flush(boolean debounce, long scheduledGeneration) {
        try {
            if (debounce && batchMillis > 0) Thread.sleep(batchMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            synchronized (lock) {
                if (generation == scheduledGeneration) flushScheduled = false;
            }
            return;
        }
        synchronized (lock) {
            if (generation != scheduledGeneration) return;
            flushScheduled = false;
        }
        agentNotify.tryNotify(() -> drainMessage(scheduledGeneration),
            response -> logger.info("[Item Pickup] AI 思考结果: {}", response));
    }

    private String drainMessage(long scheduledGeneration) {
        Map<String, Integer> batch;
        synchronized (lock) {
            if (generation != scheduledGeneration || pending.isEmpty()) return null;
            batch = new LinkedHashMap<>(pending);
            pending.clear();
        }
        String items = batch.entrySet().stream()
            .map(entry -> entry.getKey() + " x" + entry.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
        return "[SYSTEM_EVENT] 你刚刚捡起物品: " + items + "。"
            + "请结合当前任务判断是否需要检查物品栏、切换装备或继续原动作。";
    }

    private static String resolveItemName(int collectedEntityId) {
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "unknown_item_entity_" + collectedEntityId;
        }
        xin.bbtt.Entity.Entity entity = MovementSync.INSTANCE.getWorld().getEntity(collectedEntityId);
        if (entity == null) return "unknown_item_entity_" + collectedEntityId;
        Object raw = entity.getMetadata().get("item");
        if (raw instanceof ItemStack stack) {
            String name = ItemStateParser.INSTANCE.getItemName(stack.getId());
            if (name != null && !name.isBlank()) return name;
        }
        return "unknown_item_entity_" + collectedEntityId;
    }
}
