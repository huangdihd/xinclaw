/*
 *   Copyright (C) 2026 huangdihd
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    private final Map<Integer, ItemStack[]> containers = new ConcurrentHashMap<>();
    private int playerInventoryStateId = 0;

    @EventHandler
    public void onSetContent(ReceivePacketEvent<ClientboundContainerSetContentPacket> event) {
        ClientboundContainerSetContentPacket packet = event.getPacket();
        containers.put(packet.getContainerId(), packet.getItems());
        if (packet.getContainerId() == 0) {
            playerInventoryStateId = packet.getStateId();
        }
    }

    @EventHandler
    public void onSetSlot(ReceivePacketEvent<ClientboundContainerSetSlotPacket> event) {
        ClientboundContainerSetSlotPacket packet = event.getPacket();
        ItemStack[] items = containers.get(packet.getContainerId());
        if (items != null && packet.getSlot() >= 0 && packet.getSlot() < items.length) {
            items[packet.getSlot()] = packet.getItem();
        }
        if (packet.getContainerId() == 0) {
            playerInventoryStateId = packet.getStateId();
        }
    }

    public ItemStack[] getInventory() {
        return containers.get(0);
    }

    public int getPlayerInventoryStateId() {
        return playerInventoryStateId;
    }
}

