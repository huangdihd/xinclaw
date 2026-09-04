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

package xin.claw.tools;

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
import xin.claw.XinClawPlugin;
import xin.bbtt.movements.DigBlockMovement;

public class ActionTools {
    private static final Logger logger = LoggerFactory.getLogger(ActionTools.class);
    private final java.util.function.IntFunction<xin.bbtt.Entity.Entity> entityLookup;

    public ActionTools() {
        this(entityId -> MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null
            ? null : MovementSync.INSTANCE.getWorld().getEntity(entityId));
    }

    ActionTools(java.util.function.IntFunction<xin.bbtt.Entity.Entity> entityLookup) {
        this.entityLookup = java.util.Objects.requireNonNull(entityLookup, "entityLookup");
    }

    private int getLatestSequence() {
        return Bot.INSTANCE.getAndIncreaseSequence();
    }

    @Tool("与指定的实体(玩家、动物、怪物等)进行交互。可以用于攻击(ATTACK)或者右键交互(INTERACT，如骑马、与村民交易、打开箱子矿车等)。")
    public String interactEntity(
            @P("实体的 ID (可以通过 getNearbyEntities 获取)") int entityId,
            @P("交互动作，可选值: INTERACT (右键交互), ATTACK (左键攻击)") String actionStr) {
        logger.info("[AI Tool Call] interactEntity(id={}, action={})", entityId, actionStr);

        InteractAction action;
        try {
            action = InteractAction.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "无效的动作: " + actionStr + "。请使用 INTERACT 或 ATTACK。";
        }

        if (MovementSync.INSTANCE != null && MovementSync.INSTANCE.getWorld() != null) {
            xin.bbtt.Entity.Entity entity = entityLookup.apply(entityId);
            if (entity != null && entity.getPosition() != null) {
                MovementSync.INSTANCE.directLookAt(entityAimPoint(entity));
            }
        }

        // Swing hand BEFORE interaction
        Bot.INSTANCE.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        if (action == InteractAction.ATTACK) {
            Bot.INSTANCE.getSession().send(new ServerboundInteractPacket(entityId, action, false));
            return "已尝试攻击实体 ID " + entityId;
        } else {
            Bot.INSTANCE.getSession().send(new ServerboundInteractPacket(entityId, action, Hand.MAIN_HAND, false));
            return "已尝试交互实体 ID " + entityId;
        }
    }

    @Tool("立即让机器人朝向指定实体的身体中心。这是一次性转向；不会设置持续目光目标，也不会移动或与实体交互。")
    public String faceEntity(@P("实体 ID，可通过 getNearbyEntities 获取") int entityId) {
        logger.info("[AI Tool Call] faceEntity(id={})", entityId);
        if (MovementSync.INSTANCE == null) return "MovementSync 插件尚未就绪。";
        xin.bbtt.Entity.Entity entity = entityLookup.apply(entityId);
        if (entity == null || entity.getPosition() == null) return "未找到实体 ID " + entityId + "。";
        MovementSync.INSTANCE.directLookAt(entityAimPoint(entity));
        return String.format(
            "entity_id=%d yaw=%.1f pitch=%.1f",
            entityId, MovementSync.INSTANCE.yaw.get(), MovementSync.INSTANCE.pitch.get());
    }

    @Tool("设置持续方块目光目标。空闲时机器人会一直朝向该方块中心；其他需要朝向的动作可暂时覆盖，动作结束后自动恢复。")
    public String setBlockGazeTarget(@P("方块 X") int x, @P("方块 Y") int y, @P("方块 Z") int z) {
        if (MovementSync.INSTANCE == null) return "MovementSync 插件尚未就绪。";
        MovementSync.INSTANCE.setBlockGazeTarget(new org.joml.Vector3i(x, y, z));
        return "已设置持续目光目标: block=(" + x + "," + y + "," + z + ")";
    }

    @Tool("设置持续实体目光目标。空闲时机器人会跟随实体的实时位置转头；其他动作结束后自动恢复。")
    public String setEntityGazeTarget(@P("实体 ID，可通过 getNearbyEntities 获取") int entityId) {
        if (MovementSync.INSTANCE == null) return "MovementSync 插件尚未就绪。";
        xin.bbtt.Entity.Entity entity = entityLookup.apply(entityId);
        if (entity == null) return "未找到实体 ID " + entityId + "。";
        MovementSync.INSTANCE.setEntityGazeTarget(entityId);
        return "已设置持续目光目标: entity_id=" + entityId;
    }

    @Tool("查看当前持续目光目标；返回 none、方块坐标，或实体 ID 与实时位置。")
    public String getGazeTarget() {
        if (MovementSync.INSTANCE == null) return "MovementSync 插件尚未就绪。";
        return MovementSync.INSTANCE.describeGazeTarget();
    }

    @Tool("清除持续目光目标。")
    public String clearGazeTarget() {
        if (MovementSync.INSTANCE == null) return "MovementSync 插件尚未就绪。";
        MovementSync.INSTANCE.clearGazeTarget();
        return "已清除持续目光目标。";
    }

    static org.joml.Vector3d entityAimPoint(xin.bbtt.Entity.Entity entity) {
        return new org.joml.Vector3d(entity.getPosition())
            .add(0, Math.max(0.1, entity.getHeight() * 0.5), 0);
    }

    @Tool("切换机器人的主手快捷栏物品(0-8)。")
    public String changeSlot(@P("快捷栏槽位编号 (0-8)") int slot) {
        logger.info("[AI Tool Call] changeSlot(slot={})", slot);
        if (slot < 0 || slot > 8) return "槽位必须在0-8之间。";
        Bot.INSTANCE.getSession().send(new ServerboundSetCarriedItemPacket(slot));
        return "已切换到槽位 " + slot;
    }

    @Tool("使用手中的物品（右键点击一次）。")
    public String useItem() {
        logger.info("[AI Tool Call] useItem()");

        int sequence = getLatestSequence();
        
        // Swing hand BEFORE interaction
        Bot.INSTANCE.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        Bot.INSTANCE.getSession().send(new ServerboundUseItemPacket(
                Hand.MAIN_HAND,
                sequence,
                MovementSync.INSTANCE.yaw.get(),
                MovementSync.INSTANCE.pitch.get()
        ));
        
        return "已尝试使用物品 (Seq: " + sequence + ")";
    }

    @Tool("长按使用手中的物品（如吃食物、喝药水、拉满弓等），并在指定时间后自动松开。吃食物/喝药水通常需要 1600 毫秒，拉满弓通常需要 1000 毫秒。")
    public String useItemWithDuration(@P("按住右键的持续时间（毫秒）") long durationMs) {
        logger.info("[AI Tool Call] useItemWithDuration(ms={})", durationMs);

        int sequence = getLatestSequence();
        
        // Swing hand BEFORE interaction
        Bot.INSTANCE.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        Bot.INSTANCE.getSession().send(new ServerboundUseItemPacket(Hand.MAIN_HAND, sequence, MovementSync.INSTANCE.yaw.get(), MovementSync.INSTANCE.pitch.get()));

        if (XinClawPlugin.INSTANCE.executorService != null && !XinClawPlugin.INSTANCE.executorService.isShutdown()) {
            XinClawPlugin.INSTANCE.executorService.submit(() -> {
                try {
                    Thread.sleep(durationMs);
                    if (Bot.INSTANCE.getSession() != null) {
                        Bot.INSTANCE.getSession().send(new ServerboundPlayerActionPacket(
                                PlayerAction.RELEASE_USE_ITEM,
                                Vector3i.ZERO,
                                Direction.DOWN,
                                getLatestSequence()
                        ));
                    }
                } catch (InterruptedException ignored) {}
            });
        }
        return "已开始使用物品，时长 " + durationMs + "ms";
    }

    @Tool("松开使用物品的按键。")
    public String releaseUseItem() {
        logger.info("[AI Tool Call] releaseUseItem()");
        Bot.INSTANCE.getSession().send(new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO, Direction.DOWN, getLatestSequence()));
        return "已松开物品。";
    }

    @Tool("对指定的方块进行交互（右键点击）：可打开或关闭木门/活板门/栅栏门（找到 open=false 的门后，对门的下半部分坐标使用本工具即可开门）、按按钮、拉拉杆、点击按钮开门，也可放置方块或打开箱子。")
    public String interactBlock(
            @P("X") int x, @P("Y") int y, @P("Z") int z,
            @P("面: DOWN, UP, NORTH, SOUTH, WEST, EAST") String directionStr) {
        logger.info("[AI Tool Call] interactBlock({}, {}, {}, {})", x, y, z, directionStr);

        Direction direction;
        try {
            direction = Direction.valueOf(directionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "无效方向: " + directionStr;
        }

        org.joml.Vector3d currentPos = MovementSync.INSTANCE.position.get();
        double dist = currentPos.distance(new org.joml.Vector3d(x + 0.5, y + 0.5, z + 0.5));
        if (dist > 6) return "目标太远 (" + String.format("%.1f", dist) + "格)，无法交互。";

        MovementSync.INSTANCE.lookAt(new org.joml.Vector3d(x + 0.5, y + 0.5, z + 0.5));
        
        // Swing hand BEFORE interaction
        Bot.INSTANCE.getSession().send(new ServerboundSwingPacket(Hand.MAIN_HAND));

        int sequence = getLatestSequence();
        Bot.INSTANCE.getSession().send(new ServerboundUseItemOnPacket(
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

        Vector3i posInt = Vector3i.from(x, y, z);
        org.joml.Vector3d posDouble = new org.joml.Vector3d(x + 0.5, y + 0.5, z + 0.5);

        MovementSync.INSTANCE.lookAt(posDouble);

        MovementSync.INSTANCE.getMovementController().addMovement(new DigBlockMovement(posInt));
        
        return "已尝试挖掘坐标 (" + x + ", " + y + ", " + z + ")";
    }
}
