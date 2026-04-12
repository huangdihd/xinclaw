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

public class MovementTools {
    private static final Logger logger = LoggerFactory.getLogger(MovementTools.class);

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

    @Tool("智能寻路到指定的绝对坐标点 x, y, z。会自动绕过障碍物。由于性能限制，目标距离不建议超过30格。")
    public String pathfindTo(
            @P("目标 X 坐标") double x,
            @P("目标 Y 坐标") double y,
            @P("目标 Z 坐标") double z) {
        logger.info("[AI Tool Call] 调用了 pathfindTo(x={}, y={}, z={})", x, y, z);
        if (MovementSync.Instance == null || MovementSync.Instance.movementController == null) {
            return "MovementSync 插件尚未就绪，无法移动。";
        }

        Vector3d currentPos = new Vector3d(MovementSync.Instance.position.get());
        Vector3d targetPos = new Vector3d(x, y, z);

        if (currentPos.distance(targetPos) > 40) {
            return "目标距离过远(>40格)，寻路算法可能会失败或消耗过多性能。建议先使用 walkTo 靠近目标。";
        }

        java.util.List<Vector3d> path = xin.agent.AStarPathfinder.findPath(currentPos, targetPos, 40);
        
        if (path == null || path.isEmpty()) {
            return "找不到前往坐标 (" + x + ", " + y + ", " + z + ") 的有效路径，可能被完全阻挡。";
        }

        MovementSync.Instance.movementController.cancelAll(); // 清除之前的移动任务
        
        Vector3d posTracker = new Vector3d(currentPos);
        for (Vector3d waypoint : path) {
            double distance = posTracker.distance(waypoint);
            if (distance < 0.1) continue;
            
            Vector3d direction = new Vector3d(waypoint).sub(posTracker).normalize();
            Vector3d velocity = direction.mul(MovementSync.movementSpeed);
            long timeMs = (long) ((distance / MovementSync.movementSpeed) * 50);
            
            MovementSync.Instance.movementController.addMovement(new WalkMovement(velocity, timeMs));
            posTracker = waypoint;
        }

        return String.format("寻路成功！已规划包含 %d 个节点的路径，开始前往坐标 (%.2f, %.2f, %.2f)。", path.size(), x, y, z);
    }
}
