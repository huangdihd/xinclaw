package xin.agent;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.ReceivePacketEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryTracker implements Listener {

    // Map of windowId -> array of item stacks
    private final Map<Integer, ItemStack[]> containers = new ConcurrentHashMap<>();

    @EventHandler
    public void onSetContent(ReceivePacketEvent<ClientboundContainerSetContentPacket> event) {
        ClientboundContainerSetContentPacket packet = event.getPacket();
        containers.put(packet.getContainerId(), packet.getItems());
    }

    @EventHandler
    public void onSetSlot(ReceivePacketEvent<ClientboundContainerSetSlotPacket> event) {
        ClientboundContainerSetSlotPacket packet = event.getPacket();
        ItemStack[] items = containers.get(packet.getContainerId());
        if (items != null && packet.getSlot() < items.length) {
            items[packet.getSlot()] = packet.getItem();
        }
    }

    public ItemStack[] getInventory() {
        // Window 0 is the player inventory
        return containers.get(0);
    }
}
