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

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.agent.ItemStateParser;
import xin.agent.XinAgentPlugin;

public class InventoryTools {
    private static final Logger logger = LoggerFactory.getLogger(InventoryTools.class);

    @Tool("获取机器人当前的物品栏信息。用于查看自己背包装了什么东西。")
    public String getInventory() {
        logger.info("[AI Tool Call] 调用了 getInventory()");
        if (XinAgentPlugin.Instance == null || XinAgentPlugin.Instance.inventoryTracker == null) {
            return "物品栏追踪器未初始化。";
        }
        
        org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack[] items = XinAgentPlugin.Instance.inventoryTracker.getInventory();
        if (items == null) {
            return "当前还未收到物品栏数据，请稍后再试（可以尝试打开背包或等待同步）。";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("机器人物品栏信息:\n");
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
            return "物品栏目前是空的。";
        }
        
        return sb.toString();
    }
}
