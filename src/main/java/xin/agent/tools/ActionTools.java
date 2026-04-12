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

package xin.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;
import xin.bbtt.MovementSync;
import xin.agent.XinAgentPlugin;
import xin.agent.utils.RotationUtils;

import java.time.Instant;

public class ActionTools {
    private static final Logger logger = LoggerFactory.getLogger(ActionTools.class);

    private int getNextSequence() {
        if (XinAgentPlugin.Instance != null && XinAgentPlugin.Instance.sequenceTracker != null) {
            return XinAgentPlugin.Instance.sequenceTracker.getAndIncrement();
        }
        return 0;
    }

    @Tool("与指定的实体(玩家、动物、怪物等)进行交互。可以用于攻击(ATTACK)或者右键交互(INTERACT，如骑马、与村民交易、打开箱子矿车等)。")
    public String interactEntity(
            @P("实体的 ID (可以通过 getNearbyEntities 获取)") int entityId,
            @P("交互动作，可选值: INTERACT (右键交互), ATTACK (左键攻击)") String actionStr) {
        logger.info("[AI Tool Call] interactEntity(id={}, action={})", entityId, actionStr);
        if (Bot.Instance == null) return "Bot实例未初始化。";

        InteractAction action;
        try {
            action = InteractAction.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "无效的动作: " + actionStr + "。请使用 INTERACT 或 ATTACK。";
        }

        if (MovementSync.Instance != null && MovementSync.Instance.getWorld() != null) {
            xin.bbtt.Entity.Entity entity = MovementSync.Instance.getWorld().getEntity(entityId);
            if (entity != null && entity.getPosition() != null) {
                RotationUtils.instantLookAt(entity.getPosition());
            }
        }

        // Swing hand BEFORE interaction
        Bot.Instance.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        if (action == InteractAction.ATTACK) {
            Bot.Instance.getSession().send(new ServerboundInteractPacket(entityId, action, false));
            return "已尝试攻击实体 ID " + entityId;
        } else {
            Bot.Instance.getSession().send(new ServerboundInteractPacket(entityId, action, Hand.MAIN_HAND, false));
            return "已尝试交互实体 ID " + entityId;
        }
    }

    @Tool("切换机器人的主手快捷栏物品(0-8)。")
    public String changeSlot(@P("快捷栏槽位编号 (0-8)") int slot) {
        logger.info("[AI Tool Call] changeSlot(slot={})", slot);
        if (Bot.Instance == null) return "Bot实例未初始化。";
        if (slot < 0 || slot > 8) return "槽位必须在0-8之间。";
        Bot.Instance.getSession().send(new ServerboundSetCarriedItemPacket(slot));
        return "已切换到槽位 " + slot;
    }

    @Tool("使用手中的物品（右键点击一次）。")
    public String useItem() {
        logger.info("[AI Tool Call] useItem()");
        if (Bot.Instance == null) return "Bot实例未初始化。";

        int sequence = getNextSequence();
        
        // Swing hand BEFORE interaction
        Bot.Instance.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        Bot.Instance.getSession().send(new ServerboundUseItemPacket(
                Hand.MAIN_HAND,
                sequence,
                MovementSync.Instance.yaw.get(),
                MovementSync.Instance.pitch.get()
        ));
        
        return "已尝试使用物品 (Seq: " + sequence + ")";
    }

    @Tool("长按使用物品，并在指定时间后自动松开。")
    public String useItemWithDuration(@P("按住时长（毫秒）") long durationMs) {
        logger.info("[AI Tool Call] useItemWithDuration(ms={})", durationMs);
        if (Bot.Instance == null) return "Bot实例未初始化。";

        int sequence = getNextSequence();
        
        // Swing hand BEFORE interaction
        Bot.Instance.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        Bot.Instance.getSession().send(new ServerboundUseItemPacket(Hand.MAIN_HAND, sequence, MovementSync.Instance.yaw.get(), MovementSync.Instance.pitch.get()));

        if (XinAgentPlugin.Instance.executorService != null) {
            XinAgentPlugin.Instance.executorService.submit(() -> {
                try {
                    Thread.sleep(durationMs);
                    Bot.Instance.getSession().send(new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO, Direction.DOWN, getNextSequence()));
                } catch (InterruptedException ignored) {}
            });
        }
        return "已开始使用物品，时长 " + durationMs + "ms";
    }

    @Tool("松开使用物品的按键。")
    public String releaseUseItem() {
        logger.info("[AI Tool Call] releaseUseItem()");
        if (Bot.Instance == null) return "Bot实例未初始化。";
        Bot.Instance.getSession().send(new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO, Direction.DOWN, getNextSequence()));
        return "已松开物品。";
    }

    @Tool("对指定的方块进行交互（点击按钮、放置方块等）。")
    public String interactBlock(
            @P("X") int x, @P("Y") int y, @P("Z") int z,
            @P("面: DOWN, UP, NORTH, SOUTH, WEST, EAST") String directionStr) {
        logger.info("[AI Tool Call] interactBlock({}, {}, {}, {})", x, y, z, directionStr);
        if (Bot.Instance == null) return "Bot实例未初始化。";

        Direction direction;
        try {
            direction = Direction.valueOf(directionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "无效方向: " + directionStr;
        }

        org.joml.Vector3d currentPos = MovementSync.Instance.position.get();
        double dist = currentPos.distance(new org.joml.Vector3d(x + 0.5, y + 0.5, z + 0.5));
        if (dist > 6) return "目标太远 (" + String.format("%.1f", dist) + "格)，无法交互。";

        RotationUtils.instantLookAt(new org.joml.Vector3d(x + 0.5, y + 0.5, z + 0.5));
        
        // Swing hand BEFORE interaction
        Bot.Instance.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        int sequence = getNextSequence();
        Bot.Instance.getSession().send(new ServerboundUseItemOnPacket(
                Vector3i.from(x, y, z),
                direction,
                Hand.MAIN_HAND,
                0.5f, 0.5f, 0.5f,
                false,
                false, 
                sequence
        ));
        
        return String.format("已尝试交互坐标 (%d, %d, %d)，距离: %.1f, Seq: %d", x, y, z, dist, sequence);
    }

    @Tool("挖掘指定坐标的方块。")
    public String mineBlock(@P("X") int x, @P("Y") int y, @P("Z") int z) {
        logger.info("[AI Tool Call] mineBlock({}, {}, {})", x, y, z);
        if (Bot.Instance == null) return "Bot实例未初始化。";

        RotationUtils.instantLookAt(new org.joml.Vector3d(x + 0.5, y + 0.5, z + 0.5));
        
        // Swing hand
        Bot.Instance.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        int seq = getNextSequence();
        Vector3i pos = Vector3i.from(x, y, z);
        Bot.Instance.getSession().send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, pos, Direction.UP, seq));
        
        // We might need a small delay here for realistic digging, but for creative/instant:
        Bot.Instance.getSession().send(new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING, pos, Direction.UP, seq));
        
        return "已尝试挖掘坐标 (" + x + ", " + y + ", " + z + ")";
    }
}
