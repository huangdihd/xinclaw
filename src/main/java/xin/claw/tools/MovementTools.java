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
import xin.bbtt.MovementSync;
import xin.bbtt.movements.WalkMovement;
import xin.bbtt.movements.ActionMovement;
import xin.bbtt.pathfinding.DefaultPathfindingContext;
import xin.bbtt.pathfinding.DStarLite;
import xin.bbtt.pathfinding.Node;
import java.util.List;
import java.util.Optional;

public class MovementTools {
    private static final Logger logger = LoggerFactory.getLogger(MovementTools.class);

    /**
     * Navigation is dig-free unless the agent explicitly opts in. Omitting the
     * parameter (null) or passing false disables mining for the whole request:
     * pre-check, auto-repath and region probes all keep terrain intact.
     */
    static boolean resolveAllowDig(Boolean allowDig) {
        return Boolean.TRUE.equals(allowDig);
    }

    static void applyAllowDigPermission(Boolean allowDig) {
        MovementSync.setAllowDigging(resolveAllowDig(allowDig));
    }

    @Tool("让机器人行走(walk)到指定的绝对坐标点 x, y, z。这是一个持续性动作，大约每秒可以移动 4.3 格。")
    public String walkTo(
            @P("目标 X 坐标") double x,
            @P("目标 Y 坐标") double y,
            @P("目标 Z 坐标") double z) {
        
        logger.info("[AI Tool Call] 调用了 walkTo(x={}, y={}, z={})", x, y, z);
        
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.movementController == null) {
            return "MovementSync 插件尚未就绪，无法移动。";
        }

        Vector3d currentPos = new Vector3d(MovementSync.INSTANCE.position.get());
        Vector3d targetPos = new Vector3d(x, y, z);
        
        double distance = currentPos.distance(targetPos);
        if (distance < 0.1) {
            return "机器人已经在目标位置附近。";
        }

        Vector3d direction = new Vector3d(targetPos).sub(currentPos).normalize();
        Vector3d velocity = direction.mul(MovementSync.movementSpeed);
        
        long timeMs = (long) ((distance / MovementSync.movementSpeed) * 50);

        MovementSync.INSTANCE.movementController.addMovement(new WalkMovement(velocity, timeMs));
        
        return String.format("已开始走向坐标 (%.2f, %.2f, %.2f), 预计需要 %d 毫秒。", x, y, z, timeMs);
    }

    @Tool("让机器人看向(look)指定的绝对坐标点 x, y, z。这是一个几乎瞬时的动作，大约耗时 100-500 毫秒。")
    public String lookAt(
            @P("目标 X 坐标") double x,
            @P("目标 Y 坐标") double y,
            @P("目标 Z 坐标") double z) {
        
        logger.info("[AI Tool Call] 调用了 lookAt(x={}, y={}, z={})", x, y, z);
        
        if (MovementSync.INSTANCE == null) return "插件未就绪。";

        Vector3d target = new Vector3d(x, y, z);
        MovementSync.INSTANCE.lookAt(target);
        return String.format("机器人已经看向坐标 (%.2f, %.2f, %.2f)。", x, y, z);
    }

    @Tool("让机器人跳跃(jump)。这是一个物理动作，大约耗时 500 毫秒。")
    public String jump() {
        logger.info("[AI Tool Call] 调用了 jump()");
        
        if (MovementSync.INSTANCE == null) return "插件未就绪。";
        MovementSync.INSTANCE.jump();
        return "机器人已执行跳跃。";
    }

    @Tool("在移动队列中添加等待时间。仅用于连续动作之间(如:走->等->挖)。请勿将此工具作为你的最终决策，如果你当前无事可做，直接在对话中回复玩家即可，切勿循环调用此工具！")
    public String addIdleMovement(@P("等待的持续时间（毫秒）") long durationMs) {
        logger.info("[AI Tool Call] 调用了 addIdleMovement(ms={})", durationMs);
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.movementController == null) {
            return "MovementSync 插件尚未就绪。";
        }
        MovementSync.INSTANCE.movementController.addMovement(new ActionMovement(() -> {}, durationMs));
        return "已在任务队列中添加了 " + durationMs + " 毫秒的等待时间。";
    }

    @Tool("智能动态寻路到指定的绝对坐标点 x, y, z。默认不挖掘(allowDig=false)：不会破坏任何方块，遇到障碍只绕行、跳跃或搭桥。allowDig=true 才允许挖掘挡路的方块；关着的门请用 interactBlock 打开，不要挖墙。如果传入了taskId，当到达目标时该任务会自动被标记为 DONE。大约每秒移动 3-4 格。")
    public String pathfindTo(
            @P("目标 X 坐标") double x,
            @P("目标 Y 坐标") double y,
            @P("目标 Z 坐标") double z,
            @P(value = "是否允许寻路时挖掘挡路的方块；不填默认 false（不挖掘）。开门请用 interactBlock，不要靠挖掘穿墙", required = false)
            Boolean allowDig,
            @P("绑定的任务ID (可选，填入对应的任务ID可以在到达目标后系统自动将其标记为 DONE，留空则不绑定)") String taskId) {
        logger.info("[AI Tool Call] 调用了 pathfindTo(x={}, y={}, z={}, allowDig={}, taskId={})", x, y, z, allowDig, taskId);
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.movementController == null) {
            return "MovementSync 插件尚未就绪，无法移动。";
        }
        applyAllowDigPermission(allowDig);

        Vector3d currentPos = MovementSync.INSTANCE.position.get();
        Node start = new Node((int) Math.floor(currentPos.x), (int) Math.floor(currentPos.y), (int) Math.floor(currentPos.z));
        Node goal = new Node((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));

        // 预检查路径是否通畅
        DStarLite pf = new DStarLite(start, goal, MovementSync.INSTANCE.getWorld());
        List<xin.bbtt.pathfinding.PathStep> path = pf.findPath(2000); 

        if (path == null || path.size() <= 1) {
            return String.format("寻路失败：无法找到前往坐标 (%.1f, %.1f, %.1f) 的可行路径（当前挖掘许可：%s）。目标可能在加载范围外，或者被完全封死；关门建筑请先用 interactBlock 开门，或尝试先向那个方向走一段路(walkTo)再重新寻路。", x, y, z, resolveAllowDig(allowDig) ? "允许挖掘" : "禁止挖掘");
        }

        // 使用 MovementSync 1.3.3+ 内置的寻路引擎
        org.joml.Vector3i targetPos = new org.joml.Vector3i((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        MovementSync.INSTANCE.setActiveGoal(targetPos);
        MovementSync.INSTANCE.triggerAutoRepath();
        
        // 绑定任务
        if (taskId != null && !taskId.trim().isEmpty() && xin.claw.XinClawPlugin.INSTANCE != null) {
            xin.claw.XinClawPlugin.INSTANCE.currentMovementTaskId = taskId.trim();
        }

        return String.format("已启动内置寻路引擎，寻路成功（包含 %d 个节点），开始前往坐标 (%.2f, %.2f, %.2f)。挖掘许可：%s。", path.size(), x, y, z, resolveAllowDig(allowDig) ? "允许挖掘挡路方块" : "不挖掘任何方块");
    }

    @Tool({
        "预览前往指定绝对坐标点 x,y,z 的真实寻路路线，并返回寻路节点。默认不挖掘(allowDig=false)：路线不会破坏任何方块。",
        "本工具不会移动机器人：不会设置 activeGoal、不会触发自动重寻路、不会修改移动任务，也不会向移动队列加入动作。",
        "用于在调用 pathfindTo 前查看路线如何接近目标以及最后几步落在哪里；决定执行后再单独调用 pathfindTo。"
    })
    public String previewPathTo(
            @P("目标 X 绝对坐标") double x,
            @P("目标 Y 绝对坐标") double y,
            @P("目标 Z 绝对坐标") double z,
            @P(value = "是否允许寻路时挖掘挡路的方块；不填默认 false（不挖掘）", required = false)
            Boolean allowDig) {
        logger.info("[AI Tool Call] 调用了 previewPathTo(x={}, y={}, z={}, allowDig={})", x, y, z, allowDig);
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "MovementSync 世界尚未就绪，无法预览寻路节点。";
        }
        boolean digAllowed = resolveAllowDig(allowDig);
        Vector3d position = MovementSync.INSTANCE.position.get();
        if (position == null) return "路径预览失败：无法获取当前坐标。不会移动机器人。";
        Node start = new Node(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
        Node goal = new Node(
            (int) Math.floor(x),
            (int) Math.floor(y),
            (int) Math.floor(z)
        );
        DStarLite pathfinder = new DStarLite(start, goal, new DefaultPathfindingContext(
            MovementSync.INSTANCE.getWorld(), digAllowed));
        List<?> path = pathfinder.findPath(2000);
        if (path == null || path.isEmpty() || !goal.equals(pathEndpoint(path))) {
            return String.format(
                "路径预览失败：无法找到前往绝对坐标 (%d,%d,%d) 的完整路线（当前挖掘许可：%s）。不会移动机器人。",
                goal.x, goal.y, goal.z, digAllowed ? "允许挖掘" : "禁止挖掘"
            );
        }
        return formatDirectPathPreview(goal, path, digAllowed);
    }

    @Tool("在指定半开区间内寻找最近可达站立点。min 与 max_exclusive 必须直接复制 CLMCP 搜索结果返回的 [x,y,z] 三整数数组，不要拆分或重排。默认不挖掘(allowDig=false)，路线不会破坏任何方块。不会移动机器人。")
    public String findReachablePointInBounds(
            @P("最小坐标 [x,y,z]，直接复制 CLMCP 搜索结果 bounds.min") int[] min,
            @P("最大坐标 [x,y,z]，直接复制 CLMCP 搜索结果 bounds.max_exclusive") int[] max_exclusive,
            @P(value = "是否允许检查寻路时挖掘挡路的方块；不填默认 false（不挖掘）", required = false)
            Boolean allowDig) {
        logger.info(
            "[AI Tool Call] 调用了 findReachablePointInBounds(min={}, max_exclusive={}, allowDig={})",
            java.util.Arrays.toString(min), java.util.Arrays.toString(max_exclusive), allowDig
        );
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "MovementSync 世界尚未就绪，无法检查区域可达点。";
        }
        try {
            Optional<RegionPathPlanner.Result> result = findRegionTarget(min, max_exclusive, resolveAllowDig(allowDig));
            if (result.isEmpty()) {
                return "在指定半开区间内没有找到可通过寻路抵达的站立点（当前挖掘许可：" + (resolveAllowDig(allowDig) ? "允许挖掘" : "禁止挖掘") + "）。可缩小 bounds 或先移动到候选区域附近。";
            }
            RegionPathPlanner.Result target = result.get();
            return String.format(
                "找到可达站立点 (%d,%d,%d)，预计路径%d个节点；区域内共有%d个站立候选，检查了%d个候选。可将该坐标交给 pathfindTo，或直接调用 pathfindToBounds。",
                target.target().x, target.target().y, target.target().z,
                target.pathLength(), target.standableCandidates(), target.probedCandidates()
            );
        } catch (IllegalArgumentException error) {
            return "无效 bounds：" + error.getMessage();
        }
    }

    @Tool({
        "预览前往指定半开区间内最近可达站立点的真实寻路路线，返回选定目标和寻路节点。默认不挖掘(allowDig=false)：路线不会破坏任何方块。",
        "本工具不会移动机器人：不会设置 activeGoal、不会触发自动重寻路、不会修改移动任务，也不会向移动队列加入动作。",
        "用于在行动前理解路线从哪一侧接近候选、最后几步落在哪里；若决定执行，再单独调用 pathfindToBounds。min 与 max_exclusive 必须直接复制候选 bounds 的 [x,y,z] 数组。"
    })
    public String previewPathToBounds(
            @P("最小坐标 [x,y,z]，直接复制候选 bounds.min") int[] min,
            @P("最大坐标 [x,y,z]，直接复制候选 bounds.max_exclusive") int[] max_exclusive,
            @P(value = "是否允许寻路时挖掘挡路的方块；不填默认 false（不挖掘）", required = false)
            Boolean allowDig) {
        logger.info(
            "[AI Tool Call] 调用了 previewPathToBounds(min={}, max_exclusive={}, allowDig={})",
            java.util.Arrays.toString(min), java.util.Arrays.toString(max_exclusive), allowDig
        );
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null) {
            return "MovementSync 世界尚未就绪，无法预览寻路节点。";
        }
        boolean digAllowed = resolveAllowDig(allowDig);
        try {
            Optional<RegionPathPlanner.Result> result = findRegionTarget(min, max_exclusive, digAllowed);
            if (result.isEmpty()) {
                return "路径预览失败：bounds 内没有找到可达站立点（当前挖掘许可：" + (digAllowed ? "允许挖掘" : "禁止挖掘") + "）。不会移动机器人。";
            }
            RegionPathPlanner.Result target = result.get();
            Vector3d position = MovementSync.INSTANCE.position.get();
            if (position == null) return "路径预览失败：无法获取当前坐标。不会移动机器人。";
            Node start = new Node(
                (int) Math.floor(position.x),
                (int) Math.floor(position.y),
                (int) Math.floor(position.z)
            );
            DStarLite pathfinder = new DStarLite(start, target.target(), new DefaultPathfindingContext(
                MovementSync.INSTANCE.getWorld(), digAllowed));
            List<?> path = pathfinder.findPath(2000);
            if (path == null || path.isEmpty() || !target.target().equals(pathEndpoint(path))) {
                return "路径预览失败：世界状态在候选检查后发生变化，无法重建完整路线。不会移动机器人。";
            }
            return formatPathPreview(target, path, digAllowed);
        } catch (IllegalArgumentException error) {
            return "无效 bounds：" + error.getMessage();
        }
    }

    @Tool("智能寻路到指定半开区间内最近的可达站立点。min 与 max_exclusive 必须直接复制 CLMCP 搜索结果返回的 [x,y,z] 三整数数组，不要拆分或重排。默认不挖掘(allowDig=false)：不会破坏任何方块，遇到障碍只绕行、跳跃或搭桥；allowDig=true 才允许挖掘。原有 pathfindTo 保持可用。")
    public String pathfindToBounds(
            @P("最小坐标 [x,y,z]，直接复制 CLMCP 搜索结果 bounds.min") int[] min,
            @P("最大坐标 [x,y,z]，直接复制 CLMCP 搜索结果 bounds.max_exclusive") int[] max_exclusive,
            @P(value = "是否允许寻路时挖掘挡路的方块；不填默认 false（不挖掘）。开门请用 interactBlock，不要靠挖掘穿墙", required = false)
            Boolean allowDig,
            @P("绑定的任务ID；不绑定时传空字符串") String taskId) {
        logger.info(
            "[AI Tool Call] 调用了 pathfindToBounds(min={}, max_exclusive={}, allowDig={}, taskId={})",
            java.util.Arrays.toString(min), java.util.Arrays.toString(max_exclusive), allowDig, taskId
        );
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.getWorld() == null
            || MovementSync.INSTANCE.movementController == null) {
            return "MovementSync 插件尚未就绪，无法进行区域寻路。";
        }
        boolean digAllowed = resolveAllowDig(allowDig);
        applyAllowDigPermission(allowDig);
        try {
            Optional<RegionPathPlanner.Result> result = findRegionTarget(min, max_exclusive, digAllowed);
            if (result.isEmpty()) {
                return "区域寻路失败：bounds 内没有找到可达站立点（当前挖掘许可：" + (digAllowed ? "允许挖掘" : "禁止挖掘") + "）。关门建筑请先用 interactBlock 开门，或缩小 bounds、先向候选区域移动。";
            }
            RegionPathPlanner.Result target = result.get();
            org.joml.Vector3i targetPos = target.target().toVector();
            MovementSync.INSTANCE.setActiveGoal(targetPos);
            MovementSync.INSTANCE.triggerAutoRepath();
            if (taskId != null && !taskId.trim().isEmpty() && xin.claw.XinClawPlugin.INSTANCE != null) {
                xin.claw.XinClawPlugin.INSTANCE.currentMovementTaskId = taskId.trim();
            }
            return String.format(
                "已从 bounds 中选择可达站立点 (%d,%d,%d)，预检查路径%d个节点，并启动内置寻路引擎。",
                target.target().x, target.target().y, target.target().z, target.pathLength()
            );
        } catch (IllegalArgumentException error) {
            return "无效 bounds：" + error.getMessage();
        }
    }

    private Optional<RegionPathPlanner.Result> findRegionTarget(int[] min, int[] maxExclusive, boolean digAllowed) {
        Vector3d currentPosition = MovementSync.INSTANCE.position.get();
        if (currentPosition == null) return Optional.empty();
        Node start = new Node(
            (int) Math.floor(currentPosition.x),
            (int) Math.floor(currentPosition.y),
            (int) Math.floor(currentPosition.z)
        );
        RegionPathPlanner.Bounds bounds = RegionPathPlanner.Bounds.fromArrays(min, maxExclusive);
        return RegionPathPlanner.findNearestReachable(
            start,
            bounds,
            (x, y, z) -> {
                try {
                    return MovementSync.INSTANCE.getWorld().getBlockStateAt(new Vector3d(x, y, z));
                } catch (Exception error) {
                    return null;
                }
            },
            (pathStart, goal) -> {
                DStarLite pathfinder = new DStarLite(pathStart, goal, new DefaultPathfindingContext(
                    MovementSync.INSTANCE.getWorld(), digAllowed));
                List<?> candidatePath = pathfinder.findPath(2000);
                if (candidatePath == null || candidatePath.isEmpty()) return 0;
                Node endpoint = pathEndpoint(candidatePath);
                if (!goal.equals(endpoint)) return 0;
                return candidatePath.size();
            },
            64
        );
    }

    static String formatPathPreview(RegionPathPlanner.Result target, List<?> path, boolean digAllowed) {
        StringBuilder output = new StringBuilder();
        output.append("路径预览完成；不会移动机器人，也未设置任何导航目标。\n")
            .append("挖掘许可=").append(digAllowed ? "允许挖掘挡路方块" : "不挖掘任何方块").append('\n')
            .append("selected_target=(").append(target.target().x).append(',')
            .append(target.target().y).append(',').append(target.target().z).append(")")
            .append(" total_nodes=").append(path.size())
            .append(" standable_candidates=").append(target.standableCandidates())
            .append(" probed_candidates=").append(target.probedCandidates()).append("\n");
        appendPathNodes(output, path, "pathfindToBounds");
        return output.toString();
    }

    static String formatDirectPathPreview(Node target, List<?> path, boolean digAllowed) {
        StringBuilder output = new StringBuilder();
        output.append("路径预览完成；不会移动机器人，也未设置任何导航目标。\n")
            .append("挖掘许可=").append(digAllowed ? "允许挖掘挡路方块" : "不挖掘任何方块").append('\n')
            .append("selected_target=(").append(target.x).append(',')
            .append(target.y).append(',').append(target.z).append(")")
            .append(" total_nodes=").append(path.size()).append("\n");
        appendPathNodes(output, path, "pathfindTo");
        return output.toString();
    }

    private static void appendPathNodes(StringBuilder output, List<?> path, String executionTool) {
        final int maximumShown = 96;
        final int prefixCount = 16;
        int omitted = Math.max(0, path.size() - maximumShown);
        output.append("route_nodes=[");
        for (int index = 0; index < path.size(); index++) {
            if (omitted > 0 && index == prefixCount) {
                output.append("... omitted_middle_nodes=").append(omitted).append(" ..., ");
                index += omitted - 1;
                continue;
            }
            Node node = pathNode(path.get(index));
            if (node == null) {
                output.append(index).append(":UNKNOWN");
            } else {
                output.append(index).append(':').append(pathTypeName(path.get(index)))
                    .append("@(").append(node.x).append(',').append(node.y).append(',').append(node.z).append(')');
            }
            if (index + 1 < path.size()) output.append(", ");
        }
        output.append("]\n")
            .append("这里只报告当前世界快照下的路线；若要执行，请另行调用 ")
            .append(executionTool).append('。');
    }

    private static String pathTypeName(Object step) {
        if (step instanceof Node) return "WALK";
        try {
            Object type = step.getClass().getMethod("getType").invoke(step);
            if (type instanceof Enum<?> value) return value.name();
            return type == null ? "UNKNOWN" : String.valueOf(type);
        } catch (ReflectiveOperationException error) {
            return "UNKNOWN";
        }
    }

    private static Node pathNode(Object step) {
        if (step instanceof Node node) return node;
        try {
            Object node = step.getClass().getMethod("getNode").invoke(step);
            return node instanceof Node result ? result : null;
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    static Node pathEndpoint(List<?> path) {
        if (path == null || path.isEmpty()) return null;
        return pathNode(path.get(path.size() - 1));
    }

    @Tool("强制让机器人停止所有移动、寻路行为。当你想要它停下时调用此方法。")
    public String stopWalking() {
        logger.info("[AI Tool Call] 调用了 stopWalking()");
        if (MovementSync.INSTANCE == null || MovementSync.INSTANCE.movementController == null) {
            return "MovementSync 插件尚未就绪。";
        }
        MovementSync.INSTANCE.movementController.cancelAll();
        return "已成功停止所有的寻路和移动任务。";
    }

    @Tool("获取机器人当前的移动和寻路状态。当你不知道自己是不是在跑图、想知道当前目标坐标、或者想确认自己是不是被卡住时调用。")
    public String getMovementStatus() {
        logger.info("[AI Tool Call] 调用了 getMovementStatus()");
        if (MovementSync.INSTANCE == null) return "MovementSync 插件未就绪。";
        
        StringBuilder status = new StringBuilder();
        
        org.joml.Vector3i goal = MovementSync.INSTANCE.getActiveGoal();
        if (goal != null) {
            status.append(String.format("当前有活跃的寻路目标: (%d, %d, %d)。", goal.x, goal.y, goal.z));
        } else {
            status.append("当前没有活跃的寻路目标。");
        }
        
        if (MovementSync.INSTANCE.movementController != null) {
            xin.bbtt.movement.Movement current = MovementSync.INSTANCE.movementController.getCurrentMovement();
            if (current != null) {
                status.append("\n机器人目前正在执行物理移动或挖块动作 (").append(current.getClass().getSimpleName()).append(")。");
            } else {
                status.append("\n机器人目前处于静止状态(或正在等待下一个动作)。");
            }
        }
        
        int followTargetId = MovementSync.INSTANCE.getFollowTargetId();
        if (followTargetId != -1) {
            status.append("\n机器人目前正在跟随实体 ID [").append(followTargetId).append("]，可调用 stopFollowing 停止跟随。");
        }

        if (xin.claw.XinClawPlugin.INSTANCE != null && xin.claw.XinClawPlugin.INSTANCE.currentMovementTaskId != null) {
            status.append("\n当前移动系统正在为任务ID [").append(xin.claw.XinClawPlugin.INSTANCE.currentMovementTaskId).append("] 服务。");
        }

        return status.toString();
    }
}
