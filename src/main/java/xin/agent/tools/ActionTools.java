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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;

import java.time.Instant;

public class ActionTools {
    private static final Logger logger = LoggerFactory.getLogger(ActionTools.class);

    @Tool("切换机器人的主手快捷栏物品(0-8)。当你需要拿出不同物品（方块、食物、武器）时调用。")
    public String changeSlot(@P("快捷栏槽位编号 (0到8之间)") int slot) {
        logger.info("[AI Tool Call] 调用了 changeSlot(slot={})", slot);
        if (Bot.Instance == null) return "Bot实例未初始化。";
        if (slot < 0 || slot > 8) return "槽位必须在0到8之间。";

        Bot.Instance.getSession().send(new ServerboundSetCarriedItemPacket(slot));
        return "已成功切换到快捷栏第 " + slot + " 格。";
    }

    @Tool("使用手中的物品。可用于吃食物、拉弓、投掷物品等。通常调用一次代表右键点击。")
    public String useItem() {
        logger.info("[AI Tool Call] 调用了 useItem()");
        if (Bot.Instance == null) return "Bot实例未初始化。";

        int sequence = (int) Instant.now().toEpochMilli();
        Bot.Instance.getSession().send(new ServerboundUseItemPacket(
                Hand.MAIN_HAND,
                sequence,
                0, 0
        ));
        return "已尝试使用手中的物品。如果是吃东西或拉弓，可能还需要调用 releaseUseItem 来结束动作。";
    }

    @Tool("松开使用物品的按键。当你正在吃东西、喝药水或者拉弓时，调用此方法来完成动作。")
    public String releaseUseItem() {
        logger.info("[AI Tool Call] 调用了 releaseUseItem()");
        if (Bot.Instance == null) return "Bot实例未初始化。";
        
        int sequence = (int) Instant.now().toEpochMilli();
        Bot.Instance.getSession().send(new ServerboundPlayerActionPacket(
                PlayerAction.RELEASE_USE_ITEM,
                Vector3i.ZERO,
                Direction.DOWN,
                sequence
        ));
        return "已松开使用物品的按键。";
    }

    @Tool("对指定的方块进行交互（放置方块、打开箱子、按按钮等）。相当于对着该坐标右键。")
    public String interactBlock(
            @P("目标方块的 X 坐标") int x,
            @P("目标方块的 Y 坐标") int y,
            @P("目标方块的 Z 坐标") int z,
            @P("交互的面，可选值: DOWN, UP, NORTH, SOUTH, WEST, EAST") String directionStr) {
        logger.info("[AI Tool Call] 调用了 interactBlock(x={}, y={}, z={}, dir={})", x, y, z, directionStr);
        if (Bot.Instance == null) return "Bot实例未初始化。";

        Direction direction;
        try {
            direction = Direction.valueOf(directionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "无效的方向: " + directionStr + "。请使用 DOWN, UP, NORTH, SOUTH, WEST 或 EAST。";
        }

        Vector3i pos = Vector3i.from(x, y, z);
        int sequence = (int) Instant.now().toEpochMilli();
        
        Bot.Instance.getSession().send(new ServerboundUseItemOnPacket(
                pos,
                direction,
                Hand.MAIN_HAND,
                0.5f, 0.5f, 0.5f,
                false,
                sequence
        ));
        return String.format("已尝试对坐标 (%d, %d, %d) 的 %s 面进行右键交互。", x, y, z, direction.name());
    }

    @Tool("挖掘指定坐标的方块。发送开始挖掘和完成挖掘的信号。如果方块很坚硬可能需要多次调用或在生存模式下失败。")
    public String mineBlock(
            @P("目标方块的 X 坐标") int x,
            @P("目标方块的 Y 坐标") int y,
            @P("目标方块的 Z 坐标") int z) {
        logger.info("[AI Tool Call] 调用了 mineBlock(x={}, y={}, z={})", x, y, z);
        if (Bot.Instance == null) return "Bot实例未初始化。";

        Vector3i pos = Vector3i.from(x, y, z);
        int sequence = (int) Instant.now().toEpochMilli();

        // 发送开始挖掘
        Bot.Instance.getSession().send(new ServerboundPlayerActionPacket(
                PlayerAction.START_DIGGING,
                pos,
                Direction.UP,
                sequence
        ));

        // 发送结束挖掘 (对于创造模式或者瞬间破坏的方块有效)
        Bot.Instance.getSession().send(new ServerboundPlayerActionPacket(
                PlayerAction.FINISH_DIGGING,
                pos,
                Direction.UP,
                sequence + 1
        ));

        return String.format("已尝试挖掘坐标 (%d, %d, %d) 的方块。", x, y, z);
    }
}
