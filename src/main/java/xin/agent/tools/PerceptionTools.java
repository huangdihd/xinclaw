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
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.Block.BlockStateParser;
import xin.bbtt.MovementSync;
import xin.bbtt.mcbot.Bot;

import java.util.HashMap;
import java.util.Map;

public class PerceptionTools {
    private static final Logger logger = LoggerFactory.getLogger(PerceptionTools.class);

    @Tool("获取当前机器人的位置坐标和所在的服务器。当需要知道自己在哪里时调用。")
    public String whereAmI() {
        logger.info("[AI Tool Call] 调用了 whereAmI()");
        if (MovementSync.Instance == null || Bot.Instance == null) {
            return "插件或Bot实例未初始化。";
        }
        Vector3d position = new Vector3d(MovementSync.Instance.position.get());
        return String.format("当前服务器: %s, 坐标: x=%.2f, y=%.2f, z=%.2f",
                Bot.Instance.getServer(), position.x, position.y, position.z);
    }

    @Tool("获取机器人当前所在的服务器或世界名称。")
    public String getCurrentWorld() {
        logger.info("[AI Tool Call] 调用了 getCurrentWorld()");
        if (Bot.Instance == null || Bot.Instance.getServer() == null) return "未知服务器";
        
        String dimension = "未知维度";
        if (xin.agent.XinAgentPlugin.Instance != null && xin.agent.XinAgentPlugin.Instance.dimensionTracker != null) {
            dimension = xin.agent.XinAgentPlugin.Instance.dimensionTracker.getCurrentDimension();
        }
        
        return "当前所在的服务器: " + Bot.Instance.getServer().name() + "，所在维度: " + dimension;
    }

    @Tool("获取两个坐标点所构成的立方体范围内的所有方块信息。用于观察周围环境。")
    public String getBlocksInCube(
            @P("第一个点的 X 坐标") int x1,
            @P("第一个点的 Y 坐标") int y1,
            @P("第一个点的 Z 坐标") int z1,
            @P("第二个点的 X 坐标") int x2,
            @P("第二个点的 Y 坐标") int y2,
            @P("第二个点的 Z 坐标") int z2) {
        
        logger.info("[AI Tool Call] 调用了 getBlocksInCube(从 ({},{},{}) 到 ({},{},{}))", x1, y1, z1, x2, y2, z2);
        
        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) {
            return "无法获取世界信息。";
        }

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        int totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (totalBlocks > 1000) {
            return "查询范围过大，方块数量超过1000个。请缩小查询范围。";
        }

        Map<String, java.util.List<String>> blockPositions = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int blockStateId = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(x, y, z));
                    String blockName = String.valueOf(BlockStateParser.Instance.parseStateId(blockStateId));
                    
                    // 忽略空气方块以减少无用信息
                    if (blockName.contains("air") || blockName.contains("Air")) continue;
                    
                    blockPositions.computeIfAbsent(blockName, k -> new java.util.ArrayList<>())
                            .add(String.format("(%d,%d,%d)", x, y, z));
                }
            }
        }
        
        StringBuilder result = new StringBuilder();
        result.append(String.format("区域 (%d,%d,%d) 到 (%d,%d,%d) 的非空方块详情:\n", minX, minY, minZ, maxX, maxY, maxZ));
        
        if (blockPositions.isEmpty()) {
            return result.append("该区域内全为空气。").toString();
        }
        
        for (Map.Entry<String, java.util.List<String>> entry : blockPositions.entrySet()) {
            java.util.List<String> coords = entry.getValue();
            result.append(String.format("- %s (共%d个): ", entry.getKey(), coords.size()));
            
            // 为了防止Token超限，如果同种方块过多，只显示前20个的坐标
            if (coords.size() > 20) {
                result.append(String.join(", ", coords.subList(0, 20)));
                result.append(" ...等\n");
            } else {
                result.append(String.join(", ", coords));
                result.append("\n");
            }
        }
        
        return result.toString();
    }

    @Tool("获取机器人周围指定半径内特定方块（根据名称模糊匹配）的具体坐标位置。用于寻找特定的方块，如'工作台'、'钻石矿'等。")
    public String findSpecificBlocks(
            @P("要查找的方块名称(英文或ID的一部分，如'diamond', 'crafting', 'log')") String blockNameQuery,
            @P("搜索半径(方块距离，建议10-30)") double radius) {
        logger.info("[AI Tool Call] 调用了 findSpecificBlocks(query='{}', radius={})", blockNameQuery, radius);
        
        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) {
            return "无法获取世界信息。";
        }

        Vector3d center = MovementSync.Instance.position.get();
        if (center == null) return "无法获取当前坐标。";

        int r = (int) Math.ceil(radius);
        int minX = (int) center.x - r;
        int maxX = (int) center.x + r;
        int minY = (int) center.y - r;
        int maxY = (int) center.y + r;
        int minZ = (int) center.z - r;
        int maxZ = (int) center.z + r;

        int totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (totalBlocks > 64000) { // 限制最大搜索体积 (约40x40x40)
            return "查询半径过大，请缩小搜索半径（建议30以内）。";
        }

        String lowerQuery = blockNameQuery.toLowerCase();
        java.util.List<String> foundPositions = new java.util.ArrayList<>();
        String foundBlockName = null;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // 球形半径检查
                    if (center.distance(new Vector3d(x, y, z)) > radius) continue;
                    
                    int blockStateId = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(x, y, z));
                    String blockName = String.valueOf(BlockStateParser.Instance.parseStateId(blockStateId));
                    
                    if (blockName.toLowerCase().contains(lowerQuery) && !blockName.toLowerCase().contains("air")) {
                        if (foundBlockName == null) foundBlockName = blockName; // 记录第一个匹配到的完整名称
                        foundPositions.add(String.format("(%d,%d,%d)", x, y, z));
                        if (foundPositions.size() >= 50) {
                            break; // 找到50个就够了，防止Token溢出
                        }
                    }
                }
                if (foundPositions.size() >= 50) break;
            }
            if (foundPositions.size() >= 50) break;
        }
        
        if (foundPositions.isEmpty()) {
            return String.format("在半径 %.1f 内没有找到匹配 '%s' 的方块。", radius, blockNameQuery);
        }
        
        StringBuilder result = new StringBuilder();
        result.append(String.format("在半径 %.1f 内找到了匹配 '%s' 的方块 (例如: %s)，共 %d 个:\n", 
                radius, blockNameQuery, foundBlockName, foundPositions.size()));
        
        result.append(String.join(", ", foundPositions));
        if (foundPositions.size() == 50) {
            result.append(" ...（已达到最大显示数量）");
        }
        
        return result.toString();
    }

    @Tool("获取机器人周围的实体信息(玩家、怪物、掉落物等)。用于观察环境和其它玩家位置。")
    public String getNearbyEntities(@P("搜索半径(方块距离，最大建议50)") double radius) {
        logger.info("[AI Tool Call] 调用了 getNearbyEntities(radius={})", radius);

        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) {
            return "无法获取世界信息。";
        }

        Vector3d currentPos = MovementSync.Instance.position.get();
        if (currentPos == null) return "无法获取当前坐标。";

        try {
            java.lang.reflect.Field entitiesField = xin.bbtt.world.World.class.getDeclaredField("entities");
            entitiesField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<Integer, xin.bbtt.Entity.Entity> entities = (Map<Integer, xin.bbtt.Entity.Entity>) entitiesField.get(MovementSync.Instance.getWorld());
            
            if (entities == null || entities.isEmpty()) {
                return "周围没有任何实体缓存。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("以坐标(%.1f, %.1f, %.1f)为中心，半径 %.1f 内的实体:\n", currentPos.x, currentPos.y, currentPos.z, radius));
            
            int count = 0;
            for (xin.bbtt.Entity.Entity entity : entities.values()) {
                if (entity == null || entity.getPosition() == null) continue;
                
                if (MovementSync.Instance.entityId == entity.getEntityId()) continue;
                
                double distance = currentPos.distance(entity.getPosition());
                if (distance <= radius) {
                    sb.append(String.format("- [%s] ID:%d, 距离:%.1f格, 坐标:(%.1f, %.1f, %.1f)\n", 
                            entity.getType().name(), entity.getEntityId(), distance, 
                            entity.getPosition().x, entity.getPosition().y, entity.getPosition().z));
                    count++;
                }
            }
            
            if (count == 0) {
                return "搜索半径内没有发现其它实体。";
            }
            
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取实体信息失败", e);
            return "获取实体信息时发生错误。";
        }
    }
}
