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
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.agent.utils.ItemStateParser;
import xin.agent.XinAgentPlugin;
import xin.bbtt.mcbot.Bot;

public class InventoryTools {
    private static final Logger logger = LoggerFactory.getLogger(InventoryTools.class);

    @Tool("获取机器人当前的物品栏信息，或者当前打开的容器（如箱子、工作台等）的信息。")
    public String getInventory() {
        logger.info("[AI Tool Call] 调用了 getInventory()");
        if (XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.inventoryTracker == null) {
            return "物品栏追踪器未初始化。";
        }
        
        int containerId = XinAgentPlugin.Instance.inventoryTracker.getCurrentContainerId();
        org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack[] items = XinAgentPlugin.Instance.inventoryTracker.getInventory();
        
        if (items == null) {
            return "当前还未收到容器数据，请稍后再试。";
        }
        
        StringBuilder sb = new StringBuilder();
        if (containerId == 0) {
            sb.append("机器人物品栏信息 (背包):\n");
        } else {
            sb.append("当前打开的外部容器信息 (ID: ").append(containerId).append("):\n");
        }
        
        int count = 0;
        for (int i = 0; i < items.length; i++) {
            org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack item = items[i];
            if (item != null) {
                String itemName = ItemStateParser.Instance.getItemName(item.getId());
                sb.append(String.format("- Slot %d: %s x%d\n", i, itemName, item.getAmount()));
                count++;
            }
        }
        
        if (count == 0) {
            return "该容器/背包目前是空的。";
        }
        
        return sb.toString();
    }

    @Tool("关闭当前打开的容器（箱子、工作台、村民交易界面等）。")
    public String closeContainer() {
        logger.info("[AI Tool Call] 调用了 closeContainer()");
        if (Bot.Instance == null || XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.inventoryTracker == null) {
            return "未初始化。";
        }
        int containerId = XinAgentPlugin.Instance.inventoryTracker.getCurrentContainerId();
        if (containerId == 0) {
            return "当前没有打开外部容器。";
        }
        Bot.Instance.getSession().send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket(containerId));
        return "已向服务器发送关闭容器的请求。";
    }

    @Tool("点击操作机器人的物品栏（背包）或当前打开的容器中的指定槽位。可以用于移动物品、穿戴装备或丢弃物品。")
    public String clickInventorySlot(
            @P("你要点击的槽位号 (例如 0-45。通常 36-44 是快捷栏。)") int slot,
            @P("点击动作：0 代表左键单击(拿起/放下全部)，1 代表右键单击(拿起一半/放下一个)") int button) {
        logger.info("[AI Tool Call] 调用了 clickInventorySlot(slot={}, button={})", slot, button);

        if (Bot.Instance == null || XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.inventoryTracker == null) {
            return "Bot或物品栏追踪器未初始化。";
        }

        int containerId = XinAgentPlugin.Instance.inventoryTracker.getCurrentContainerId();
        int stateId = XinAgentPlugin.Instance.inventoryTracker.getCurrentStateId();
        
        ClickItemAction action = button == 1 ? ClickItemAction.RIGHT_CLICK : ClickItemAction.LEFT_CLICK;
        
        Bot.Instance.getSession().send(new ServerboundContainerClickPacket(
                containerId,
                stateId,
                slot,
                ContainerActionType.CLICK_ITEM,
                action,
                null,
                new Int2ObjectOpenHashMap<>()
        ));

        return "已向服务器发送点击容器 (ID: " + containerId + ") 的槽位 " + slot + " 的请求（动作：" + action.name() + "）。";
    }
}

