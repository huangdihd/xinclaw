package xin.claw.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.MovementSync;
import xin.bbtt.events.DeathEvent;
import xin.bbtt.mcbot.Utils;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.claw.XinClawPlugin;

public class DeathListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(DeathListener.class);
    private long lastDeathNotifyTime = 0;

    @EventHandler
    public void onDeath(DeathEvent event) {
        // 不取消默认动作，MovementSync 会自动发送 RESPAWN 请求重生
        String deathMessage = event.getMessage() == null ? "(无死亡信息)" : Utils.toString(event.getMessage());
        logger.info("[DeathListener] Bot 死亡: {}", deathMessage);

        // 死亡后旧寻路目标已无意义，清掉防止重生后朝旧目标乱跑
        if (MovementSync.INSTANCE != null) {
            MovementSync.INSTANCE.cancelNavigation();
        }
        XinClawPlugin plugin = XinClawPlugin.INSTANCE;
        if (plugin == null || plugin.agentManager == null) return;
        plugin.invalidateTaskLoopWork();

        // 反复被杀(如重生点蹲守)时防刷屏
        long now = System.currentTimeMillis();
        if (now - lastDeathNotifyTime < 15000) return;
        lastDeathNotifyTime = now;

        String msg = String.format(
                "[SYSTEM_EVENT] 你刚刚死亡了！死亡信息: %s。系统已自动请求重生，你很快会在重生点复活。" +
                "你的物品已掉落在死亡地点附近(约5分钟后消失)，所有正在进行的移动和寻路已被取消。" +
                "请结合死亡原因评估：是否安全回去捡装备、是否需要调整或暂停当前任务。",
                deathMessage);
        plugin.agentManager.submitMessage(msg, null,
                response -> logger.info("[Death Event] AI 思考结果: {}", response));
    }
}
