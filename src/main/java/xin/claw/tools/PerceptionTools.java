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
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.Block.BlockState;
import xin.bbtt.Block.BlockStateParser;
import xin.bbtt.MovementSync;
import xin.bbtt.mcbot.Bot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class PerceptionTools {
    private static final Logger logger = LoggerFactory.getLogger(PerceptionTools.class);

    @FunctionalInterface
    interface BlockStateLookup {
        BlockState stateAt(int x, int y, int z);
    }

    private final BlockStateLookup blockStateLookup;
    private final Supplier<Vector3d> positionLookup;

    public PerceptionTools() {
        this(
            (x, y, z) -> {
                try {
                    if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) return null;
                    int stateId = MovementSync.INSTANCE.getWorld().getBlockAt(new Vector3d(x, y, z));
                    return BlockStateParser.Instance.parseStateId(stateId);
                } catch (Exception error) {
                    return null;
                }
            },
            () -> MovementSync.INSTANCE == null ? null : MovementSync.INSTANCE.position.get()
        );
    }

    PerceptionTools(BlockStateLookup blockStateLookup, Supplier<Vector3d> positionLookup) {
        this.blockStateLookup = Objects.requireNonNull(blockStateLookup, "blockStateLookup");
        this.positionLookup = Objects.requireNonNull(positionLookup, "positionLookup");
    }

    /** 解析指定坐标的方块状态，区块未加载或状态未知时返回 null。 */
    private BlockState stateAt(int x, int y, int z) {
        return blockStateLookup.stateAt(x, y, z);
    }

    private String blockNameAt(int x, int y, int z) {
        BlockState state = stateAt(x, y, z);
        return state == null ? "未知" : state.blockName();
    }

    /** 把相对偏移转成 AI 容易理解的方位描述，如 "东2 南1 下3"。 */
    private static String relativeDesc(int dx, int dy, int dz) {
        StringBuilder sb = new StringBuilder();
        if (dx > 0) sb.append("东").append(dx).append(" ");
        else if (dx < 0) sb.append("西").append(-dx).append(" ");
        if (dz > 0) sb.append("南").append(dz).append(" ");
        else if (dz < 0) sb.append("北").append(-dz).append(" ");
        if (dy > 0) sb.append("上").append(dy).append(" ");
        else if (dy < 0) sb.append("下").append(-dy).append(" ");
        return sb.length() == 0 ? "原地" : sb.toString().trim();
    }

    @Tool("环顾四周：一次性获取脚下/身体/头顶的方块、东南西北四个方向的通畅情况、头顶净空、以及附近的危险方块(岩浆/火/仙人掌等)。这是了解自身处境的首选感知工具。")
    public String scanSurroundings() {
        logger.info("[AI Tool Call] 调用了 scanSurroundings()");
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "无法获取世界信息。";
        }
        Vector3d pos = MovementSync.INSTANCE.position.get();
        if (pos == null) return "无法获取当前坐标。";

        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("你站在 (%d, %d, %d)。\n", bx, by, bz));

        // 脚下与身体
        sb.append("脚下方块: ").append(blockNameAt(bx, by - 1, bz)).append("\n");
        BlockState feet = stateAt(bx, by, bz);
        BlockState head = stateAt(bx, by + 1, bz);
        if (feet != null && feet.isLiquid()) {
            sb.append("注意：你正泡在 ").append(feet.blockName()).append(" 里！\n");
        }
        if (head != null && head.isSolid()) {
            sb.append("警告：你的头部位置是实体方块(").append(head.blockName()).append(")，可能正在窒息！\n");
        }

        // 头顶净空
        int clearance = 0;
        for (int dy = 2; dy <= 12; dy++) {
            BlockState s = stateAt(bx, by + dy, bz);
            if (s != null && !s.isPassable()) break;
            clearance++;
        }
        sb.append("头顶净空: ").append(clearance >= 11 ? "11格以上(可能露天)" : clearance + "格").append("\n");

        // 四个水平方向的通畅情况（在脚部与头部两层取更严格者）
        sb.append("四向通畅情况(检测6格内最近的阻挡):\n");
        int[][] dirs = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        String[] dirNames = {"北(-Z)", "南(+Z)", "东(+X)", "西(-X)"};
        for (int i = 0; i < 4; i++) {
            String result = "6格内畅通";
            for (int d = 1; d <= 6; d++) {
                int x = bx + dirs[i][0] * d;
                int z = bz + dirs[i][1] * d;
                BlockState atFeet = stateAt(x, by, z);
                BlockState atHead = stateAt(x, by + 1, z);
                BlockState blocking = (atFeet != null && !atFeet.isPassable()) ? atFeet
                        : (atHead != null && !atHead.isPassable()) ? atHead : null;
                if (blocking != null) {
                    result = d + "格处被 " + blocking.blockName() + " 挡住";
                    break;
                }
            }
            sb.append("- ").append(dirNames[i]).append(": ").append(result).append("\n");
        }

        // 脚下深渊检测
        int dropDepth = -1;
        for (int dy = 1; dy <= 10; dy++) {
            BlockState below = stateAt(bx, by - dy, bz);
            if (below == null || !below.isPassable()) {
                dropDepth = dy - 1;
                break;
            }
        }
        if (dropDepth < 0) {
            sb.append("警告：你脚下10格以内没有任何落脚点，谨防坠落！\n");
        } else if (dropDepth >= 4) {
            sb.append("注意：你悬在").append(dropDepth).append("格高的空中(或站在边缘)，坠落会受伤。\n");
        }

        // 危险方块扫描 (半径4)
        String[] hazardKeywords = {"lava", "fire", "magma", "cactus", "sweet_berry", "powder_snow"};
        java.util.List<String> hazards = new java.util.ArrayList<>();
        outer:
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockState s = stateAt(bx + dx, by + dy, bz + dz);
                    if (s == null) continue;
                    String name = s.blockName().toLowerCase();
                    for (String keyword : hazardKeywords) {
                        if (name.contains(keyword)) {
                            hazards.add(s.blockName() + "(" + relativeDesc(dx, dy, dz) + ")");
                            if (hazards.size() >= 8) break outer;
                            break;
                        }
                    }
                }
            }
        }
        if (!hazards.isEmpty()) {
            sb.append("危险方块: ").append(String.join(", ", hazards)).append("\n");
        } else {
            sb.append("半径4格内没有发现危险方块。\n");
        }

        return sb.toString();
    }

    @Tool("生成以机器人为中心的俯视分层字符地图，用于直观理解周围布局、规划路线或建筑。每层是一个2D网格：上=北(-Z)，下=南(+Z)，左=西(-X)，右=东(+X)，每个字符代表1个方块。'@'=你的位置，'.'=可通行空间，'~'=水，'!'=岩浆，其余字母含义见图例。")
    public String getAreaMap(
            @P("水平半径(2-8，建议5)") int radius,
            @P("起始层相对你脚部的偏移(如 -1 表示从脚下一层开始)") int yFrom,
            @P("结束层相对你脚部的偏移(如 1 表示到头部层为止)。最多同时显示5层") int yTo) {
        logger.info("[AI Tool Call] 调用了 getAreaMap(radius={}, yFrom={}, yTo={})", radius, yFrom, yTo);
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "无法获取世界信息。";
        }
        Vector3d pos = MovementSync.INSTANCE.position.get();
        if (pos == null) return "无法获取当前坐标。";

        int r = Math.max(2, Math.min(8, radius));
        if (yFrom > yTo) { int tmp = yFrom; yFrom = yTo; yTo = tmp; }
        yFrom = Math.max(-4, Math.min(4, yFrom));
        yTo = Math.max(-4, Math.min(4, yTo));
        if (yTo - yFrom > 4) yTo = yFrom + 4; // 最多5层

        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);

        final String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Map<String, Character> legend = new java.util.LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("以你(%d, %d, %d)为中心、半径%d的俯视地图。方向: 上=北(-Z) 下=南(+Z) 左=西(-X) 右=东(+X)。\n", bx, by, bz, r));

        for (int dy = yTo; dy >= yFrom; dy--) { // 从高到低打印，符合俯视直觉
            String layerNote = dy == 0 ? "你的脚部层" : (dy == 1 ? "你的头部层" : (dy < 0 ? "脚下" + (-dy) + "层" : "头顶上方"));
            sb.append(String.format("── y=%d (相对%+d, %s) ──\n", by + dy, dy, layerNote));
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) {
                        sb.append('@');
                        continue;
                    }
                    BlockState s = stateAt(bx + dx, by + dy, bz + dz);
                    if (s == null) {
                        sb.append('?');
                    } else if (s.isLiquid()) {
                        sb.append(s.blockName().contains("lava") ? '!' : '~');
                    } else if (s.isPassable()) {
                        sb.append('.');
                    } else {
                        Character symbol = legend.get(s.blockName());
                        if (symbol == null) {
                            symbol = legend.size() < letters.length() ? letters.charAt(legend.size()) : '#';
                            legend.put(s.blockName(), symbol);
                        }
                        sb.append(symbol);
                    }
                }
                sb.append('\n');
            }
        }

        sb.append("图例: @=你 .=可通行 ~=水 !=岩浆 ?=未加载");
        if (legend.containsValue('#')) sb.append(" #=其他方块");
        sb.append("\n");
        for (Map.Entry<String, Character> entry : legend.entrySet()) {
            if (entry.getValue() != '#') {
                sb.append(entry.getValue()).append("=").append(entry.getKey()).append(" ");
            }
        }
        return sb.toString();
    }

    @Tool("直接使用 searchVoxelRegion 返回的半开 bounds 生成候选区域俯视分层地图，不会移动机器人。min 与 max_exclusive 必须原样复制 Rank-1 bounds；mapMinY/mapMaxYExclusive 选择 bounds 内最多5个绝对Y层。用于 CLMCP 候选内部的几何细化、入口和内部定位；不得脱离候选范围扫描任意远程坐标。水平边长最多32格。")
    public String getAreaMapAt(
            @P("最小坐标三整数数组 [x,y,z]，包含；直接复制 searchVoxelRegion.bounds.min") int[] min,
            @P("最大坐标三整数数组 [x,y,z]，不包含；直接复制 searchVoxelRegion.bounds.max_exclusive") int[] max_exclusive,
            @P("要显示的最小绝对Y，包含；必须位于bounds内，通常使用当前已知地面Y-1") int mapMinY,
            @P("要显示的最大绝对Y，不包含；最多比mapMinY大5，通常使用当前已知地面Y+4") int mapMaxYExclusive) {
        RegionPathPlanner.Bounds bounds;
        try {
            bounds = RegionPathPlanner.Bounds.fromArrays(min, max_exclusive);
        } catch (IllegalArgumentException error) {
            return "无效 bounds：" + error.getMessage();
        }
        int width = bounds.maxXExclusive() - bounds.minX();
        int depth = bounds.maxZExclusive() - bounds.minZ();
        if (width > 32 || depth > 32) {
            return "候选地图水平边长不得超过32格；请缩小 bounds。";
        }
        if (mapMinY < bounds.minY() || mapMaxYExclusive > bounds.maxYExclusive()
                || mapMaxYExclusive <= mapMinY) {
            return "绝对Y层必须是 bounds 内的正半开区间。";
        }
        if (mapMaxYExclusive - mapMinY > 5) {
            return "候选地图最多5层；请缩小绝对Y范围。";
        }
        int centerX = (bounds.minX() + bounds.maxXExclusive() - 1) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZExclusive() - 1) / 2;
        int radius = Math.max(2, Math.max((width + 1) / 2, (depth + 1) / 2));
        logger.info(
            "[AI Tool Call] 调用了 getAreaMapAt(min=[{},{},{}], max_exclusive=[{},{},{}], mapY=[{},{}))",
            bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxXExclusive(),
            bounds.maxYExclusive(), bounds.maxZExclusive(), mapMinY, mapMaxYExclusive
        );
        return String.format(
            "来源 bounds=[[%d,%d,%d],[%d,%d,%d])，绝对Y层=[%d,%d)。水平投影以中心(%d,%d)、半径%d完整覆盖候选；偶数边长可能含1格居中余量。%n",
            bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxXExclusive(),
            bounds.maxYExclusive(), bounds.maxZExclusive(), mapMinY, mapMaxYExclusive,
            centerX, centerZ, radius
        ) + getAreaMapAtPoint(
            centerX, mapMinY, centerZ, radius, 0, mapMaxYExclusive - mapMinY - 1
        );
    }

    @Tool("生成以任意绝对坐标为中心的俯视分层字符地图，不会移动机器人。用于普通地图分析；CLMCP 导航任务的候选细化应优先使用接收 Rank-1 bounds 的 getAreaMapAt，避免手算中心和半径。方向: 上=北(-Z)，下=南(+Z)，左=西(-X)，右=东(+X)。")
    public String getAreaMapAtPoint(
            @P("地图中心 X 绝对坐标") int centerX,
            @P("地图中心 Y 绝对坐标") int centerY,
            @P("地图中心 Z 绝对坐标") int centerZ,
            @P("水平半径(2-16，建议5-10)") int radius,
            @P("起始层相对中心Y的偏移") int yFrom,
            @P("结束层相对中心Y的偏移，最多显示5层") int yTo) {
        logger.info(
            "[AI Tool Call] 调用了 getAreaMapAtPoint(center=({},{},{}), radius={}, yFrom={}, yTo={})",
            centerX, centerY, centerZ, radius, yFrom, yTo
        );

        int r = Math.max(2, Math.min(16, radius));
        if (yFrom > yTo) { int tmp = yFrom; yFrom = yTo; yTo = tmp; }
        yFrom = Math.max(-8, Math.min(8, yFrom));
        yTo = Math.max(-8, Math.min(8, yTo));
        if (yTo - yFrom > 4) yTo = yFrom + 4;

        Vector3d player = positionLookup.get();
        int playerX = player == null ? Integer.MIN_VALUE : (int) Math.floor(player.x);
        int playerY = player == null ? Integer.MIN_VALUE : (int) Math.floor(player.y);
        int playerZ = player == null ? Integer.MIN_VALUE : (int) Math.floor(player.z);

        final String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Map<String, Character> legend = new java.util.LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "以指定中心(%d, %d, %d)为中心、半径%d的俯视地图。方向: 上=北(-Z) 下=南(+Z) 左=西(-X) 右=东(+X)。\n",
            centerX, centerY, centerZ, r
        ));

        for (int dy = yTo; dy >= yFrom; dy--) {
            int y = centerY + dy;
            sb.append(String.format("── y=%d (相对中心%+d) ──\n", y, dy));
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (x == playerX && z == playerZ && (y == playerY || y == playerY + 1)) {
                        sb.append('@');
                        continue;
                    }
                    if (dx == 0 && dz == 0 && dy == 0) {
                        sb.append('+');
                        continue;
                    }
                    BlockState state = stateAt(x, y, z);
                    if (state == null) {
                        sb.append('?');
                    } else if (state.isLiquid()) {
                        sb.append(state.blockName().contains("lava") ? '!' : '~');
                    } else if (state.isPassable()) {
                        sb.append('.');
                    } else {
                        Character symbol = legend.get(state.blockName());
                        if (symbol == null) {
                            symbol = legend.size() < letters.length() ? letters.charAt(legend.size()) : '#';
                            legend.put(state.blockName(), symbol);
                        }
                        sb.append(symbol);
                    }
                }
                sb.append('\n');
            }
        }

        sb.append("图例: +=指定中心 @=你 .=可通行 ~=水 !=岩浆 ?=未加载");
        if (legend.containsValue('#')) sb.append(" #=其他方块");
        sb.append("\n");
        for (Map.Entry<String, Character> entry : legend.entrySet()) {
            if (entry.getValue() != '#') {
                sb.append(entry.getValue()).append("=").append(entry.getKey()).append(" ");
            }
        }
        return sb.toString();
    }

    @Tool("获取当前机器人的位置坐标、所在的服务器以及朝向。当需要知道自己在哪里和面朝哪个方向时调用。")
    public String whereAmI() {
        logger.info("[AI Tool Call] 调用了 whereAmI()");
        if (MovementSync.INSTANCE == null || Bot.INSTANCE == null) {
            return "插件或Bot实例未初始化。";
        }
        Vector3d position = new Vector3d(MovementSync.INSTANCE.position.get());
        float yaw = MovementSync.INSTANCE.yaw.get();
        float pitch = MovementSync.INSTANCE.pitch.get();
        
        float normalizedYaw = yaw % 360;
        if (normalizedYaw < 0) {
            normalizedYaw += 360;
        }
        String facing;
        if (normalizedYaw >= 315 || normalizedYaw < 45) {
            facing = "南(South, +Z)";
        } else if (normalizedYaw >= 45 && normalizedYaw < 135) {
            facing = "西(West, -X)";
        } else if (normalizedYaw >= 135 && normalizedYaw < 225) {
            facing = "北(North, -Z)";
        } else {
            facing = "东(East, +X)";
        }
        String pitchDir = pitch < -45 ? "向上看" : (pitch > 45 ? "向下看" : "平视");

        String dimension = "未知维度";
        if (xin.claw.XinClawPlugin.INSTANCE != null && xin.claw.XinClawPlugin.INSTANCE.dimensionTracker != null) {
            dimension = xin.claw.XinClawPlugin.INSTANCE.dimensionTracker.getCurrentDimension();
        }
        String standingOn = blockNameAt((int) Math.floor(position.x), (int) Math.floor(position.y) - 1, (int) Math.floor(position.z));

        return String.format("当前服务器: %s, 维度: %s, 坐标: x=%.2f, y=%.2f, z=%.2f, 朝向: %s, 视角: %s (Yaw: %.1f, Pitch: %.1f), 脚下方块: %s",
                Bot.INSTANCE.getServer(), dimension, position.x, position.y, position.z, facing, pitchDir, yaw, pitch, standingOn);
    }

    @Tool("获取机器人当前的生存体征：血量、饥饿值和饱和度。当需要判断是否要吃东西、回血或避险时调用。")
    public String getVitals() {
        logger.info("[AI Tool Call] 调用了 getVitals()");
        if (xin.claw.XinClawPlugin.INSTANCE == null || xin.claw.XinClawPlugin.INSTANCE.healthTracker == null) {
            return "体征追踪器未初始化。";
        }
        xin.claw.trackers.HealthTracker tracker = xin.claw.XinClawPlugin.INSTANCE.healthTracker;
        String summary = tracker.getVitalsSummary();
        if (tracker.getHealth() <= 0) {
            return summary + "。你已经死亡，正在等待重生！";
        }
        if (tracker.getHealth() < 6) {
            return summary + "。警告：血量危急，请立即避险或回血！";
        }
        if (tracker.getFood() <= 6) {
            return summary + "。提示：饥饿值过低，无法奔跑且即将开始扣血，请尽快进食。";
        }
        return summary;
    }

    @Tool("获取机器人当前所在的服务器或世界名称。")
    public String getCurrentWorld() {
        logger.info("[AI Tool Call] 调用了 getCurrentWorld()");
        if (Bot.INSTANCE == null || Bot.INSTANCE.getServer() == null) return "未知服务器";
        
        String dimension = "未知维度";
        if (xin.claw.XinClawPlugin.INSTANCE != null && xin.claw.XinClawPlugin.INSTANCE.dimensionTracker != null) {
            dimension = xin.claw.XinClawPlugin.INSTANCE.dimensionTracker.getCurrentDimension();
        }
        
        return "当前所在的服务器: " + Bot.INSTANCE.getServer().name() + "，所在维度: " + dimension;
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
        
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
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
                    int blockStateId = MovementSync.INSTANCE.getWorld().getBlockAt(new Vector3d(x, y, z));
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
        
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "无法获取世界信息。";
        }

        Vector3d center = MovementSync.INSTANCE.position.get();
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
        record FoundBlock(int x, int y, int z, double dist, String name) {}
        java.util.List<FoundBlock> found = new java.util.ArrayList<>();

        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // 球形半径检查
                    double dist = center.distance(new Vector3d(x, y, z));
                    if (dist > radius) continue;

                    BlockState state = stateAt(x, y, z);
                    if (state == null) continue;
                    String blockName = state.blockName();

                    if (blockName.toLowerCase().contains(lowerQuery) && !blockName.toLowerCase().contains("air")) {
                        found.add(new FoundBlock(x, y, z, dist, blockName));
                    }
                }
            }
        }

        if (found.isEmpty()) {
            return String.format("在半径 %.1f 内没有找到匹配 '%s' 的方块。", radius, blockNameQuery);
        }

        // 按距离从近到远排序，只展示最近的30个
        found.sort(java.util.Comparator.comparingDouble(FoundBlock::dist));
        int shown = Math.min(found.size(), 30);

        StringBuilder result = new StringBuilder();
        result.append(String.format("在半径 %.1f 内共找到 %d 个匹配 '%s' 的方块，按距离从近到远列出前 %d 个:\n",
                radius, found.size(), blockNameQuery, shown));

        for (int i = 0; i < shown; i++) {
            FoundBlock b = found.get(i);
            result.append(String.format("- %s (%d,%d,%d) 距离%.1f格 [%s]\n",
                    b.name(), b.x(), b.y(), b.z(), b.dist(), relativeDesc(b.x() - cx, b.y() - cy, b.z() - cz)));
        }
        if (found.size() > shown) {
            result.append("...其余 ").append(found.size() - shown).append(" 个更远的已省略。");
        }

        return result.toString();
    }

    @Tool("在指定绝对坐标半开区间内模糊搜索方块名称。min 与 max_exclusive 必须直接复制 searchVoxelRegion 返回的三整数数组，格式为 min:[x,y,z]、max_exclusive:[x,y,z]，不要拆分或重排坐标。")
    public String findSpecificBlocksInBounds(
            @P("要查找的方块名称或ID片段，如'door', 'stairs', 'dark_oak'") String blockNameQuery,
            @P("最小坐标三整数数组 [x,y,z]，包含；直接复制 searchVoxelRegion.bounds.min") int[] min,
            @P("最大坐标三整数数组 [x,y,z]，不包含；直接复制 searchVoxelRegion.bounds.max_exclusive") int[] max_exclusive,
            @P("最多返回多少个坐标(1-100，建议30)") int limit) {
        if (blockNameQuery == null || blockNameQuery.isBlank()) {
            return "方块名称查询不能为空。";
        }
        RegionPathPlanner.Bounds bounds;
        try {
            bounds = RegionPathPlanner.Bounds.fromArrays(min, max_exclusive);
        } catch (IllegalArgumentException error) {
            return "无效 bounds：" + error.getMessage();
        }
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxXExclusive = bounds.maxXExclusive();
        int maxYExclusive = bounds.maxYExclusive();
        int maxZExclusive = bounds.maxZExclusive();
        logger.info(
            "[AI Tool Call] 调用了 findSpecificBlocksInBounds(query='{}', min=[{},{},{}], max_exclusive=[{},{},{}], limit={})",
            blockNameQuery, minX, minY, minZ, maxXExclusive, maxYExclusive, maxZExclusive, limit
        );

        long sizeX = (long) maxXExclusive - minX;
        long sizeY = (long) maxYExclusive - minY;
        long sizeZ = (long) maxZExclusive - minZ;
        long volume = sizeX * sizeY * sizeZ;
        if (volume > 262_144L) {
            return "查询范围过大：半开区间体积不得超过262144个方块。请缩小 bounds。";
        }

        Vector3d player = positionLookup.get();
        Vector3d reference = player == null
            ? new Vector3d(minX + sizeX / 2.0, minY + sizeY / 2.0, minZ + sizeZ / 2.0)
            : new Vector3d(player);
        int referenceX = (int) Math.floor(reference.x);
        int referenceY = (int) Math.floor(reference.y);
        int referenceZ = (int) Math.floor(reference.z);
        int outputLimit = Math.max(1, Math.min(100, limit));
        String lowerQuery = blockNameQuery.toLowerCase();
        record FoundBlock(int x, int y, int z, double distance, String name) {}
        java.util.List<FoundBlock> found = new java.util.ArrayList<>();

        for (int x = minX; x < maxXExclusive; x++) {
            for (int y = minY; y < maxYExclusive; y++) {
                for (int z = minZ; z < maxZExclusive; z++) {
                    BlockState state = stateAt(x, y, z);
                    if (state == null || state.blockName() == null) continue;
                    String name = state.blockName();
                    String lowerName = name.toLowerCase();
                    if (lowerName.contains(lowerQuery) && !lowerName.contains("air")) {
                        found.add(new FoundBlock(x, y, z, reference.distance(new Vector3d(x, y, z)), name));
                    }
                }
            }
        }

        if (found.isEmpty()) {
            return String.format(
                "在半开区间 [(%d,%d,%d),(%d,%d,%d)) 内没有找到匹配 '%s' 的方块。",
                minX, minY, minZ, maxXExclusive, maxYExclusive, maxZExclusive, blockNameQuery
            );
        }

        found.sort(java.util.Comparator.comparingDouble(FoundBlock::distance));
        int shown = Math.min(found.size(), outputLimit);
        StringBuilder result = new StringBuilder();
        result.append(String.format(
            "在半开区间 [(%d,%d,%d),(%d,%d,%d)) 内共找到 %d 个匹配 '%s' 的方块，按距%s从近到远列出前%d个:\n",
            minX, minY, minZ, maxXExclusive, maxYExclusive, maxZExclusive,
            found.size(), blockNameQuery, player == null ? "区域中心" : "你", shown
        ));
        for (int index = 0; index < shown; index++) {
            FoundBlock block = found.get(index);
            result.append(String.format(
                "- %s (%d,%d,%d) 距离%.1f格 [%s]\n",
                block.name(), block.x(), block.y(), block.z(), block.distance(),
                relativeDesc(block.x() - referenceX, block.y() - referenceY, block.z() - referenceZ)
            ));
        }
        if (found.size() > shown) {
            result.append("...其余 ").append(found.size() - shown).append(" 个已省略。");
        }
        return result.toString();
    }

    @Tool("获取机器人周围的实体信息(玩家、怪物、掉落物等)。用于观察环境和其它玩家位置。")
    public String getNearbyEntities(@P("搜索半径(方块距离，最大建议50)") double radius) {
        logger.info("[AI Tool Call] 调用了 getNearbyEntities(radius={})", radius);

        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "无法获取世界信息。";
        }

        Vector3d currentPos = MovementSync.INSTANCE.position.get();
        if (currentPos == null) return "无法获取当前坐标。";

        try {
            java.lang.reflect.Field entitiesField = xin.bbtt.world.World.class.getDeclaredField("entities");
            entitiesField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Map<Integer, xin.bbtt.Entity.Entity> entities = (Map<Integer, xin.bbtt.Entity.Entity>) entitiesField.get(MovementSync.INSTANCE.getWorld());
            
            if (entities == null || entities.isEmpty()) {
                return "周围没有任何实体缓存。";
            }

            record FoundEntity(xin.bbtt.Entity.Entity entity, double distance) {}
            java.util.List<FoundEntity> nearby = new java.util.ArrayList<>();
            for (xin.bbtt.Entity.Entity entity : entities.values()) {
                if (entity == null || entity.getPosition() == null) continue;
                if (MovementSync.INSTANCE.entityId == entity.getEntityId()) continue;

                double distance = currentPos.distance(entity.getPosition());
                if (distance <= radius) {
                    nearby.add(new FoundEntity(entity, distance));
                }
            }

            if (nearby.isEmpty()) {
                return "搜索半径内没有发现其它实体。";
            }

            // 按距离从近到远排序，最多展示40个
            nearby.sort(java.util.Comparator.comparingDouble(FoundEntity::distance));
            int shown = Math.min(nearby.size(), 40);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("以坐标(%.1f, %.1f, %.1f)为中心，半径 %.1f 内共有 %d 个实体(按距离从近到远):\n",
                    currentPos.x, currentPos.y, currentPos.z, radius, nearby.size()));

            for (int i = 0; i < shown; i++) {
                xin.bbtt.Entity.Entity entity = nearby.get(i).entity();
                sb.append(String.format("- [%s] ID:%d, 距离:%.1f格, 坐标:(%.1f, %.1f, %.1f)\n",
                        entity.getType().name(), entity.getEntityId(), nearby.get(i).distance(),
                        entity.getPosition().x, entity.getPosition().y, entity.getPosition().z));
            }
            if (nearby.size() > shown) {
                sb.append("...其余 ").append(nearby.size() - shown).append(" 个更远的已省略。");
            }

            return sb.toString();
        } catch (Exception e) {
            logger.error("获取实体信息失败", e);
            return "获取实体信息时发生错误。";
        }
    }
}
