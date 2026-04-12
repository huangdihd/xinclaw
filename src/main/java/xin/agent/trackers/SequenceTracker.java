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

package xin.agent.trackers;

import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.ReceivePacketEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceTracker implements Listener {
    private final AtomicInteger sequence = new AtomicInteger(0);

    @EventHandler
    public void onBlockChangedAck(ReceivePacketEvent<ClientboundBlockChangedAckPacket> event) {
        sequence.set(event.getPacket().getSequence());
    }

    public int getSequence() {
        return sequence.get();
    }
    
    public int getAndIncrement() {
        return sequence.getAndIncrement();
    }
}
