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
import xin.bbtt.MovementSync;
import xin.bbtt.mcbot.Bot;

import java.util.Map;

public class SocialTools {
    private static final Logger logger = LoggerFactory.getLogger(SocialTools.class);

    @Tool("获取机器人在配置文件中设置的主人(Owner)名称。")
    public String getBotOwner() {
        logger.info("[AI Tool Call] 调用了 getBotOwner()");
        if (Bot.Instance == null || Bot.Instance.getConfig() == null) return "无法读取配置。";
        String owner = Bot.Instance.getConfig().getConfigData().getOwner();
        return "机器人的主人是: " + (owner == null ? "未设置" : owner);
    }

    @Tool("获取当前服务器在线的玩家列表。")
    public String getPlayerList() {
        logger.info("[AI Tool Call] 调用了 getPlayerList()");
        if (Bot.Instance == null || Bot.Instance.players == null || Bot.Instance.players.isEmpty()) {
            return "当前没有在线玩家信息。";
        }
        
        java.util.List<String> playerNames = Bot.Instance.players.values().stream()
                .map(org.geysermc.mcprotocollib.auth.GameProfile::getName)
                .toList();
        
        return "当前在线玩家 (" + playerNames.size() + "人): " + String.join(", ", playerNames);
    }

    @Tool("获取机器人周围的玩家详细信息（包括玩家名、距离、坐标等）。比 getNearbyEntities 更专注于玩家。")
    public String getNearbyPlayers(@P("搜索半径(方块距离，建议30-50)") double radius) {
        logger.info("[AI Tool Call] 调用了 getNearbyPlayers(radius={})", radius);

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
                return "周围没有发现任何实体。";
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            
            for (xin.bbtt.Entity.Entity entity : entities.values()) {
                if (entity == null || entity.getPosition() == null) continue;
                
                // 仅筛选玩家类型且排除自己
                if (entity.getType() == org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType.PLAYER 
                    && MovementSync.Instance.entityId != entity.getEntityId()) {
                    
                    double distance = currentPos.distance(entity.getPosition());
                    if (distance <= radius) {
                        String playerName = "未知玩家";
                        if (Bot.Instance.players != null) {
                            org.geysermc.mcprotocollib.auth.GameProfile profile = Bot.Instance.players.get(entity.getUuid());
                            if (profile != null) playerName = profile.getName();
                        }
                        
                        sb.append(String.format("- 玩家: %s, 距离: %.1f格, 坐标: (%.1f, %.1f, %.1f), 实体ID: %d\n", 
                                playerName, distance, 
                                entity.getPosition().x, entity.getPosition().y, entity.getPosition().z,
                                entity.getEntityId()));
                        count++;
                    }
                }
            }
            
            if (count == 0) return "半径 " + radius + " 内没有发现其他玩家。";
            
            return "发现周围有 " + count + " 名玩家:\n" + sb.toString();
        } catch (Exception e) {
            logger.error("获取玩家信息失败", e);
            return "获取周围玩家信息时发生错误。";
        }
    }

    @Tool("根据实体 ID (Entity ID) 获取对应的玩家名称。常用于将 getNearbyEntities 返回的 ID 转换为具体的玩家名。")
    public String getPlayerNameByEntityId(@P("实体的 ID") int entityId) {
        logger.info("[AI Tool Call] 调用了 getPlayerNameByEntityId(id={})", entityId);
        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) return "世界未加载。";
        
        xin.bbtt.Entity.Entity entity = MovementSync.Instance.getWorld().getEntity(entityId);
        if (entity == null) return "未找到 ID 为 " + entityId + " 的实体（可能已离开渲染范围）。";
        
        java.util.UUID uuid = entity.getUuid();
        if (Bot.Instance.players != null) {
            org.geysermc.mcprotocollib.auth.GameProfile profile = Bot.Instance.players.get(uuid);
            if (profile != null) {
                return "实体 ID " + entityId + " 对应的玩家是: " + profile.getName();
            }
        }
        
        return "实体 ID " + entityId + " 对应的实体类型为 " + entity.getType().name() + "，但未关联到在线玩家名称。";
    }
}
