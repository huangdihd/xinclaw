package xin.claw.listeners;

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
import xin.claw.XinClawPlugin;

public class MovementTeleportListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(MovementTeleportListener.class);
    private long lastTeleportNotifyTime = 0;

    @EventHandler
    public void onTeleport(TeleportEvent event) {
        // Intercept TeleportEvent to prevent MovementSync from calling cancelAll()
        // when server syncs player position.
        event.setDefaultActionCancelled(true);
        logger.debug("[MovementTeleportListener] Received teleport event for ID {}, syncing position without cancelling path...", event.getTeleportId());

        Vector3d pos = event.getPosition();
        if (pos != null) {
            MovementSync.INSTANCE.position.set(pos);
            // We let the bot keep its current pitch/yaw during pathfinding, since TeleportEvent
            // does not expose them. We reset velocity so we don't carry over huge momentum
            MovementSync.INSTANCE.velocity.set(new Vector3d());
            updateMotionTask.checkOnGround();
            
            // 传送事件触发 AI (加入 15 秒冷却时间防止 2b2t 频繁拉回导致刷屏)
            long now = System.currentTimeMillis();
            if (!XinClawPlugin.INSTANCE.isTeleportAgentNotificationsSuppressed()
                    && now - lastTeleportNotifyTime > 15000) {
                lastTeleportNotifyTime = now;
                
                if (XinClawPlugin.INSTANCE.executorService != null && !XinClawPlugin.INSTANCE.executorService.isShutdown()) {
                    XinClawPlugin.INSTANCE.executorService.submit(() -> {
                        if (XinClawPlugin.INSTANCE.agentManager != null && !XinClawPlugin.INSTANCE.agentManager.isProcessing()) {
                            String msg = String.format("[SYSTEM_EVENT] 服务器将你传送(或拉回)到了坐标: (%.1f, %.1f, %.1f)。可能的原因包括：你刚刚复活、使用了传送指令、或者在寻路中卡在方块里被服务器拉回。如果是在寻路中被卡住，请检查是否需要停下(stopWalking)或破坏周围的障碍物。", pos.x, pos.y, pos.z);
                            String response = XinClawPlugin.INSTANCE.agentManager.processMessage(msg);
                            if (response != null && !response.trim().isEmpty()) {
                                logger.info("[Teleport Event] AI 思考结果: {}", response);
                            }
                        }
                    });
                }
            }
        }

        Session session = Bot.INSTANCE.getSession();
        if (session != null) {
            session.send(new ServerboundAcceptTeleportationPacket(event.getTeleportId()));
        }
    }
}
