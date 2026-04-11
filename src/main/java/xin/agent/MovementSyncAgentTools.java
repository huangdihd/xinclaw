package xin.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.Block.BlockStateParser;
import xin.bbtt.MovementSync;
import xin.bbtt.mcbot.Bot;
import xin.bbtt.movements.WalkMovement;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MovementSyncAgentTools {

    private static final Logger logger = LoggerFactory.getLogger(MovementSyncAgentTools.class);

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

    @Tool("让机器人行走(walk)到指定的绝对坐标点 x, y, z。")
    public String walkTo(
            @P("目标 X 坐标") double x,
            @P("目标 Y 坐标") double y,
            @P("目标 Z 坐标") double z) {
        
        logger.info("[AI Tool Call] 调用了 walkTo(x={}, y={}, z={})", x, y, z);
        
        if (MovementSync.Instance == null || MovementSync.Instance.movementController == null) {
            return "MovementSync 插件尚未就绪，无法移动。";
        }

        Vector3d currentPos = new Vector3d(MovementSync.Instance.position.get());
        Vector3d targetPos = new Vector3d(x, y, z);
        
        double distance = currentPos.distance(targetPos);
        if (distance < 0.1) {
            return "机器人已经在目标位置附近。";
        }

        Vector3d direction = new Vector3d(targetPos).sub(currentPos).normalize();
        Vector3d velocity = direction.mul(MovementSync.movementSpeed);
        
        long timeMs = (long) ((distance / MovementSync.movementSpeed) * 50);

        MovementSync.Instance.movementController.addMovement(new WalkMovement(velocity, timeMs));
        
        return String.format("已开始走向坐标 (%.2f, %.2f, %.2f), 预计需要 %d 毫秒。", x, y, z, timeMs);
    }

    @Tool("让机器人看向(look)指定的绝对坐标点 x, y, z。")
    public String lookAt(
            @P("目标 X 坐标") double x,
            @P("目标 Y 坐标") double y,
            @P("目标 Z 坐标") double z) {
        
        logger.info("[AI Tool Call] 调用了 lookAt(x={}, y={}, z={})", x, y, z);
        
        if (MovementSync.Instance == null) return "插件未就绪。";

        Vector3d target = new Vector3d(x, y, z);
        MovementSync.Instance.lookAt(target);
        return String.format("机器人已经看向坐标 (%.2f, %.2f, %.2f)。", x, y, z);
    }

    @Tool("让机器人跳跃(jump)。当遇到障碍物或者上台阶时调用此方法。")
    public String jump() {
        logger.info("[AI Tool Call] 调用了 jump()");
        
        if (MovementSync.Instance == null) return "插件未就绪。";
        MovementSync.Instance.jump();
        return "机器人已执行跳跃。";
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

        Map<String, Integer> blockCounts = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int blockStateId = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(x, y, z));
                    String blockName = String.valueOf(BlockStateParser.Instance.parseStateId(blockStateId));
                    blockCounts.put(blockName, blockCounts.getOrDefault(blockName, 0) + 1);
                }
            }
        }
        
        StringBuilder result = new StringBuilder();
        result.append(String.format("区域 (%d,%d,%d) 到 (%d,%d,%d) 的方块统计:\n", minX, minY, minZ, maxX, maxY, maxZ));
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            result.append(String.format("- %s: %d个\n", entry.getKey(), entry.getValue()));
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

    @Tool("在游戏内发送聊天消息。这将被服务器内的所有人看到。")
    public String sendChatMessage(@P("你要发送的文本内容") String message) {
        logger.info("[AI Tool Call] 调用了 sendChatMessage(message='{}')", message);
        if (Bot.Instance == null) return "Bot实例未初始化。";
        sendChatMessageInChunks(message);
        return "消息已成功分段发送至游戏内聊天框。";
    }

    private void sendChatMessageInChunks(String text) {
        int byteLimit = 90;
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.Instance.sendChatMessage(currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.Instance.sendChatMessage(currentChunk.toString());
        }
    }

    @Tool("在游戏内执行指令。注意：不需要在开头加上'/'，系统会自动加上'/'。\n" +
          "可用指令示例：\n" +
          "- kill: 自杀\n" +
          "- tell <玩家名> <内容>: 私聊\n" +
          "- chat <玩家名>: 屏蔽/解除屏蔽某玩家\n" +
          "- sc list: 查看屏蔽玩家列表\n" +
          "- stat <玩家名>: 查询玩家基础信息")
    public String sendCommand(@P("你要执行的指令文本(不带'/')") String command) {
        logger.info("[AI Tool Call] 调用了 sendCommand(command='{}')", command);
        if (Bot.Instance == null) return "Bot实例未初始化。";
        
        if (command.startsWith("tell ") || command.startsWith("msg ") || command.startsWith("w ")) {
            String[] parts = command.split(" ", 3);
            if (parts.length == 3) {
                String cmdType = parts[0];
                String recipient = parts[1];
                String text = parts[2];
                sendTellInChunks(cmdType, recipient, text);
                return "指令已分段执行。";
            }
        }
        
        Bot.Instance.sendCommand(command);
        return "指令执行请求已发送。";
    }

    private void sendTellInChunks(String cmdType, String recipient, String text) {
        int byteLimit = 80; 
        StringBuilder currentChunk = new StringBuilder();
        int currentBytes = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            
            if (currentBytes + charBytes > byteLimit) {
                Bot.Instance.sendCommand(cmdType + " " + recipient + " " + currentChunk.toString());
                currentChunk = new StringBuilder();
                currentBytes = 0;
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
            }
            
            currentChunk.append(c);
            currentBytes += charBytes;
        }
        
        if (currentChunk.length() > 0) {
            Bot.Instance.sendCommand(cmdType + " " + recipient + " " + currentChunk.toString());
        }
    }

    @Tool("获取指定指令的自动补全建议。用于查询指令的具体参数选项。")
    public String getCommandCompletions(@P("部分输入的指令文本(不带'/')") String partialCommand) {
        logger.info("[AI Tool Call] 调用了 getCommandCompletions(partialCommand='{}')", partialCommand);
        if (Bot.Instance == null || Bot.Instance.getPluginManager() == null) return "Bot实例未初始化。";
        
        java.util.List<String> completions = Bot.Instance.getPluginManager().commands().callComplete(partialCommand);
        if (completions == null || completions.isEmpty()) {
            return "没有找到该指令的补全建议。";
        }
        
        return "补全建议: " + String.join(", ", completions);
    }

    @Tool("获取机器人当前所在的服务器或世界名称。")
    public String getCurrentWorld() {
        logger.info("[AI Tool Call] 调用了 getCurrentWorld()");
        if (Bot.Instance == null || Bot.Instance.getServer() == null) return "未知服务器";
        return "当前所在的服务器/世界: " + Bot.Instance.getServer().name();
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

    @Tool("获取机器人在配置文件中设置的主人(Owner)名称。")
    public String getBotOwner() {
        logger.info("[AI Tool Call] 调用了 getBotOwner()");
        if (Bot.Instance == null || Bot.Instance.getConfig() == null) return "无法读取配置。";
        String owner = Bot.Instance.getConfig().getConfigData().getOwner();
        return "机器人的主人是: " + (owner == null ? "未设置" : owner);
    }

    @Tool("清除机器人的所有对话记忆（历史记录）。当你发现机器人胡言乱语或者需要开始新话题时使用。")
    public String clearMemory() {
        logger.info("[AI Tool Call] 调用了 clearMemory()");
        if (XinAgentPlugin.Instance != null && XinAgentPlugin.Instance.agentManager != null) {
            XinAgentPlugin.Instance.agentManager.clearMemory();
            return "记忆已清除，我们可以重新开始了。";
        }
        return "清除记忆失败，插件未就绪。";
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
