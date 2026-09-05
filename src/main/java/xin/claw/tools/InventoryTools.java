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
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.claw.utils.ItemStateParser;
import xin.claw.XinClawPlugin;
import xin.bbtt.mcbot.Bot;

public class InventoryTools {
    private static final Logger logger = LoggerFactory.getLogger(InventoryTools.class);

    @Tool("获取机器人当前的物品栏信息，或者当前打开的容器（如箱子、工作台等）的信息。")
    public String getInventory() {
        logger.info("[AI Tool Call] 调用了 getInventory()");
        if (XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.inventoryTracker == null) {
            return "物品栏追踪器未初始化。";
        }
        
        int containerId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentContainerId();
        org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack[] items = XinClawPlugin.INSTANCE.inventoryTracker.getInventory();
        
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
                String itemName = ItemStateParser.INSTANCE.getItemName(item.getId());
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
        if (Bot.INSTANCE == null || XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.inventoryTracker == null) {
            return "未初始化。";
        }
        int containerId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentContainerId();
        if (containerId == 0) {
            return "当前没有打开外部容器。";
        }
        Bot.INSTANCE.getSession().send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket(containerId));
        return "已向服务器发送关闭容器的请求。";
    }

    @Tool("点击操作机器人的物品栏（背包）或当前打开的容器中的指定槽位。可以用于移动物品、穿戴装备或丢弃物品。")
    public String clickInventorySlot(
            @P("你要点击的槽位号 (例如 0-45。通常 36-44 是快捷栏。)") int slot,
            @P("点击动作：0 代表左键单击(拿起/放下全部)，1 代表右键单击(拿起一半/放下一个)") int button) {
        logger.info("[AI Tool Call] 调用了 clickInventorySlot(slot={}, button={})", slot, button);

        if (Bot.INSTANCE == null || XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.inventoryTracker == null) {
            return "Bot或物品栏追踪器未初始化。";
        }

        int containerId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentContainerId();
        int stateId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentStateId();
        
        ClickItemAction action = button == 1 ? ClickItemAction.RIGHT_CLICK : ClickItemAction.LEFT_CLICK;
        
        Bot.INSTANCE.getSession().send(new ServerboundContainerClickPacket(
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

    @Tool("把当前打开的箱子或其他外部容器中指定槽位的一整组物品快速转移到机器人背包。只允许外部容器槽，不会把背包物品反向放进容器。结果是服务器请求；可再次调用 getInventory 确认。")
    public String takeContainerItem(@P("外部容器槽位号，来自 getInventory 输出") int slot) {
        logger.info("[AI Tool Call] takeContainerItem(slot={})", slot);
        if (Bot.INSTANCE == null || XinClawPlugin.INSTANCE == null
                || XinClawPlugin.INSTANCE.inventoryTracker == null) {
            return "Bot或物品栏追踪器未初始化。";
        }
        int containerId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentContainerId();
        if (containerId == 0) return "当前没有打开外部容器。";
        org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack[] items =
            XinClawPlugin.INSTANCE.inventoryTracker.getInventory();
        if (items == null) return "当前容器内容尚未同步，请稍后重试。";
        int externalSlotCount = Math.max(0, items.length - 36);
        if (slot < 0 || slot >= externalSlotCount) {
            return "槽位不属于外部容器；有效范围为 0-" + Math.max(0, externalSlotCount - 1) + "。";
        }
        org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack item = items[slot];
        if (item == null || item.getAmount() <= 0) return "指定容器槽位为空。";

        int stateId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentStateId();
        Bot.INSTANCE.getSession().send(new ServerboundContainerClickPacket(
            containerId,
            stateId,
            slot,
            ContainerActionType.SHIFT_CLICK_ITEM,
            ShiftClickItemAction.LEFT_CLICK,
            null,
            new Int2ObjectOpenHashMap<>()
        ));
        String itemName = ItemStateParser.INSTANCE.getItemName(item.getId());
        return String.format(
            "已请求把容器槽位 %d 的 %s x%d 快速转移到背包；请再次调用 getInventory 确认服务器结果。",
            slot, itemName, item.getAmount());
    }

    @Tool("把当前打开外部容器时，玩家背包区域指定槽位的一整组物品快速放入容器。只允许窗口中的玩家背包槽，不会误取容器物品。结果是服务器请求；可再次调用 getInventory 确认。")
    public String putInventoryItemIntoContainer(
            @P("玩家背包在当前容器窗口中的槽位号，来自 getInventory 输出") int slot) {
        logger.info("[AI Tool Call] putInventoryItemIntoContainer(slot={})", slot);
        if (Bot.INSTANCE == null || XinClawPlugin.INSTANCE == null
                || XinClawPlugin.INSTANCE.inventoryTracker == null) {
            return "Bot或物品栏追踪器未初始化。";
        }
        int containerId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentContainerId();
        if (containerId == 0) return "当前没有打开外部容器。";
        org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack[] items =
            XinClawPlugin.INSTANCE.inventoryTracker.getInventory();
        if (items == null) return "当前容器内容尚未同步，请稍后重试。";
        int externalSlotCount = Math.max(0, items.length - 36);
        if (slot < externalSlotCount || slot >= items.length) {
            return String.format(
                "槽位不属于玩家背包区域；当前窗口有效范围为 %d-%d。",
                externalSlotCount, Math.max(externalSlotCount, items.length - 1));
        }
        org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack item = items[slot];
        if (item == null || item.getAmount() <= 0) return "指定玩家背包槽位为空。";

        int stateId = XinClawPlugin.INSTANCE.inventoryTracker.getCurrentStateId();
        Bot.INSTANCE.getSession().send(new ServerboundContainerClickPacket(
            containerId,
            stateId,
            slot,
            ContainerActionType.SHIFT_CLICK_ITEM,
            ShiftClickItemAction.LEFT_CLICK,
            null,
            new Int2ObjectOpenHashMap<>()
        ));
        String itemName = ItemStateParser.INSTANCE.getItemName(item.getId());
        return String.format(
            "已请求把玩家背包槽位 %d 的 %s x%d 快速放入容器；请再次调用 getInventory 确认服务器结果。",
            slot, itemName, item.getAmount());
    }
}

