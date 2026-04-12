package xin.agent.trackers;

import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.ReceivePacketEvent;

public class DimensionTracker implements Listener {

    private String currentDimension = "未知维度";

    @EventHandler
    public void onLogin(ReceivePacketEvent<ClientboundLoginPacket> event) {
        ClientboundLoginPacket packet = event.getPacket();
        currentDimension = packet.getCommonPlayerSpawnInfo().getWorldName().asString();
    }

    @EventHandler
    public void onRespawn(ReceivePacketEvent<ClientboundRespawnPacket> event) {
        ClientboundRespawnPacket packet = event.getPacket();
        currentDimension = packet.getCommonPlayerSpawnInfo().getWorldName().asString();
    }

    public String getCurrentDimension() {
        return currentDimension;
    }
}
