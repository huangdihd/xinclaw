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

package xin.claw.trackers;

import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.claw.PluginConfig;
import xin.claw.XinClawPlugin;
import xin.bbtt.mcbot.event.EventHandler;
import xin.bbtt.mcbot.event.Listener;
import xin.bbtt.mcbot.events.ReceivePacketEvent;

public class HealthTracker implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(HealthTracker.class);

    private volatile float health = 20.0f;
    private volatile int food = 20;
    private volatile float saturation = 5.0f;
    private volatile long lastLowHealthNotifyTime = 0;

    @EventHandler
    public void onSetHealth(ReceivePacketEvent<ClientboundSetHealthPacket> event) {
        ClientboundSetHealthPacket packet = event.getPacket();
        float previousHealth = health;
        health = packet.getHealth();
        food = packet.getFood();
        saturation = packet.getSaturation();

        // 低血量且仍在下降时主动唤醒 AI（带冷却防刷屏，且 AI 空闲时才唤醒）
        if (health > 0 && health < PluginConfig.lowHealthThreshold && health < previousHealth) {
            long now = System.currentTimeMillis();
            if (now - lastLowHealthNotifyTime > 30000) {
                lastLowHealthNotifyTime = now;
                notifyAgent(String.format(
                        "[SYSTEM_EVENT] 警告：你的血量过低！当前血量 %.1f/20，饥饿值 %d/20，且血量仍在下降(刚才为 %.1f)。" +
                        "请立即评估险情：查看周围实体(getNearbyEntities)判断是否被攻击，必要时逃跑、吃食物回血或紧急避险。生存优先于一切任务！",
                        health, food, previousHealth));
            }
        }
    }

    private void notifyAgent(String message) {
        XinClawPlugin plugin = XinClawPlugin.INSTANCE;
        if (plugin == null || plugin.agentManager == null) return;
        if (plugin.agentManager.isProcessing()) return; // AI 正在思考时不打断，避免干扰当前决策

        plugin.agentManager.submitMessage(message, null,
                response -> logger.info("[Low Health Event] AI 思考结果: {}", response));
    }

    public float getHealth() {
        return health;
    }

    public int getFood() {
        return food;
    }

    public float getSaturation() {
        return saturation;
    }

    /** 供其他模块拼接状态提示用的一行体征摘要。 */
    public String getVitalsSummary() {
        return String.format("血量: %.1f/20, 饥饿值: %d/20, 饱和度: %.1f", health, food, saturation);
    }
}
