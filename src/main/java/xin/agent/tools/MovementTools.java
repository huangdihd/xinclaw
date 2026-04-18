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
import xin.bbtt.movements.WalkMovement;
import xin.bbtt.movements.ActionMovement;
import xin.bbtt.pathfinding.DStarLite;
import xin.bbtt.pathfinding.Node;
import java.util.List;

public class MovementTools {
    private static final Logger logger = LoggerFactory.getLogger(MovementTools.class);

    @Tool("让机器人行走(walk)到指定的绝对坐标点 x, y, z。这是一个持续性动作，大约每秒可以移动 4.3 格。")
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

    @Tool("让机器人看向(look)指定的绝对坐标点 x, y, z。这是一个几乎瞬时的动作，大约耗时 100-500 毫秒。")
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

    @Tool("让机器人跳跃(jump)。这是一个物理动作，大约耗时 500 毫秒。")
    public String jump() {
        logger.info("[AI Tool Call] 调用了 jump()");
        
        if (MovementSync.Instance == null) return "插件未就绪。";
        MovementSync.Instance.jump();
        return "机器人已执行跳跃。";
    }

    @Tool("在移动任务队列中添加一段等待时间（不执行任何移动）。当你需要机器人停在原地等待一会儿再执行下一个动作时使用。")
    public String addIdleMovement(@P("等待的持续时间（毫秒）") long durationMs) {
        logger.info("[AI Tool Call] 调用了 addIdleMovement(ms={})", durationMs);
        if (MovementSync.Instance == null || MovementSync.Instance.movementController == null) {
            return "MovementSync 插件尚未就绪。";
        }
        MovementSync.Instance.movementController.addMovement(new ActionMovement(() -> {}, durationMs));
        return "已在任务队列中添加了 " + durationMs + " 毫秒的等待时间。";
    }

    @Tool("智能动态寻路到指定的绝对坐标点 x, y, z。会自动绕过障碍物、自动开路(挖方块)或搭桥。如果传入了taskId，当到达目标时该任务会自动被标记为 DONE。大约每秒移动 3-4 格。")
    public String pathfindTo(
            @P("目标 X 坐标") double x,
            @P("目标 Y 坐标") double y,
            @P("目标 Z 坐标") double z,
            @P("绑定的任务ID (可选，填入对应的任务ID可以在到达目标后系统自动将其标记为 DONE，留空则不绑定)") String taskId) {
        logger.info("[AI Tool Call] 调用了 pathfindTo(x={}, y={}, z={}, taskId={})", x, y, z, taskId);
        if (MovementSync.Instance == null || MovementSync.Instance.movementController == null) {
            return "MovementSync 插件尚未就绪，无法移动。";
        }

        Vector3d currentPos = MovementSync.Instance.position.get();
        Node start = new Node((int) Math.floor(currentPos.x), (int) Math.floor(currentPos.y), (int) Math.floor(currentPos.z));
        Node goal = new Node((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));

        // 预检查路径是否通畅
        DStarLite pf = new DStarLite(start, goal, MovementSync.Instance.getWorld());
        List<Node> path = pf.findPath(2000); 

        if (path == null || path.size() <= 1) {
            return String.format("寻路失败：无法找到前往坐标 (%.1f, %.1f, %.1f) 的可行路径。目标可能在加载范围外，或者被完全封死。你可以尝试先向那个方向走一段路(walkTo)再重新寻路。", x, y, z);
        }

        // 使用 MovementSync 1.3.3+ 内置的寻路引擎
        org.joml.Vector3i targetPos = new org.joml.Vector3i((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        MovementSync.Instance.setActiveGoal(targetPos);
        MovementSync.Instance.triggerAutoRepath();
        
        // 绑定任务
        if (taskId != null && !taskId.trim().isEmpty() && xin.agent.XinAgentPlugin.Instance != null) {
            xin.agent.XinAgentPlugin.Instance.currentMovementTaskId = taskId.trim();
        }

        return String.format("已启动内置寻路引擎，寻路成功（包含 %d 个节点），开始前往坐标 (%.2f, %.2f, %.2f)。机器人会自动处理障碍物。", path.size(), x, y, z);
    }

    @Tool("强制让机器人停止所有移动、寻路行为。当你想要它停下时调用此方法。")
    public String stopWalking() {
        logger.info("[AI Tool Call] 调用了 stopWalking()");
        if (MovementSync.Instance == null || MovementSync.Instance.movementController == null) {
            return "MovementSync 插件尚未就绪。";
        }
        MovementSync.Instance.movementController.cancelAll();
        return "已成功停止所有的寻路和移动任务。";
    }
}
