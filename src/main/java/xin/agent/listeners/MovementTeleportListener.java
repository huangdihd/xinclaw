package xin.agent.listeners;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.MovementSync;
import xin.bbtt.events.TeleportEvent;
import xin.bbtt.mcbot.Bot;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.tasks.updateMotionTask;

public class MovementTeleportListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(MovementTeleportListener.class);

    @EventHandler
    public void onTeleport(TeleportEvent event) {
        // Intercept TeleportEvent to prevent MovementSync from calling cancelAll()
        // when server syncs player position.
        event.setDefaultActionCancelled(true);
        logger.info("[MovementTeleportListener] Received teleport event for ID {}, syncing position without cancelling path...", event.getTeleportId());

        Vector3d pos = event.getPosition();
        if (pos != null) {
            MovementSync.Instance.position.set(pos);
            // We let the bot keep its current pitch/yaw during pathfinding, since TeleportEvent
            // does not expose them. We reset velocity so we don't carry over huge momentum
            MovementSync.Instance.velocity.set(new Vector3d());
            updateMotionTask.checkOnGround();
        }

        Session session = Bot.Instance.getSession();
        if (session != null) {
            session.send(new ServerboundAcceptTeleportationPacket(event.getTeleportId()));
        }
    }
}
