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

package xin.agent.pathfinding;

import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.MovementSync;
import xin.bbtt.movement.Movement;

import java.util.List;

public class DynamicPathMovement extends Movement {
    private static final Logger logger = LoggerFactory.getLogger(DynamicPathMovement.class);
    
    private final Vector3d finalTarget;
    private final int maxRadius;
    private List<Vector3d> currentPath;
    private int currentWaypointIndex;
    
    private long lastRecalculateTime = 0;
    private Vector3d lastPos = null;
    private int stuckTicks = 0;

    public DynamicPathMovement(Vector3d finalTarget, int maxRadius) {
        this.finalTarget = finalTarget;
        this.maxRadius = maxRadius;
        this.currentPath = null;
        this.currentWaypointIndex = 0;
    }

    @Override
    public void init() {
        logger.info("[DynamicPathMovement] 开始智能动态寻路到目标: ({}, {}, {})", finalTarget.x, finalTarget.y, finalTarget.z);
        recalculatePath();
    }

    private void recalculatePath() {
        if (MovementSync.Instance == null) return;
        Vector3d pos = MovementSync.Instance.position.get();
        if (pos == null) return;

        this.currentPath = DStarPathfinder.findPath(pos, finalTarget, maxRadius);
        this.currentWaypointIndex = 0;
        this.lastRecalculateTime = System.currentTimeMillis();
        
        if (this.currentPath == null || this.currentPath.isEmpty()) {
            logger.warn("[DynamicPathMovement] 寻路失败：找不到可行路径。");
        } else {
            logger.info("[DynamicPathMovement] 寻路规划成功，包含 {} 个节点。", this.currentPath.size());
        }
    }

    @Override
    public void onTick() {
        if (MovementSync.Instance == null) return;
        Vector3d pos = MovementSync.Instance.position.get();
        if (pos == null) return;

        // 1. 到达检查 (0.5格范围内视为到达)
        if (pos.distanceSquared(finalTarget) < 0.25) {
            stopHorizontalMovement();
            return;
        }

        // 2. 无路重算
        if (currentPath == null || currentPath.isEmpty() || currentWaypointIndex >= currentPath.size()) {
            if (System.currentTimeMillis() - lastRecalculateTime > 1500) {
                recalculatePath();
            }
            if (currentPath == null || currentPath.isEmpty()) {
                stopHorizontalMovement();
                return;
            }
        }

        // 3. 卡住检查与脱困
        if (lastPos != null && pos.distanceSquared(lastPos) < 0.0001) {
            stuckTicks++;
            if (stuckTicks == 5) {
                MovementSync.Instance.jump(); // 尝试跳跃脱困
            }
            if (stuckTicks > 20) { // 持续卡住 1 秒
                logger.warn("[DynamicPathMovement] 机器人卡住了，尝试重规划...");
                stuckTicks = 0;
                recalculatePath();
                return;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = new Vector3d(pos);

        // 4. 路点更新
        Vector3d currentWaypoint = currentPath.get(currentWaypointIndex);
        double dx = currentWaypoint.x - pos.x;
        double dz = currentWaypoint.z - pos.z;
        double dist2D = Math.sqrt(dx * dx + dz * dz);

        // 如果已经足够接近当前路点，切到下一个
        if (dist2D < 0.5) {
            currentWaypointIndex++;
            if (currentWaypointIndex >= currentPath.size()) {
                stopHorizontalMovement();
                return;
            }
            currentWaypoint = currentPath.get(currentWaypointIndex);
            dx = currentWaypoint.x - pos.x;
            dz = currentWaypoint.z - pos.z;
            dist2D = Math.sqrt(dx * dx + dz * dz);
        }

        // 5. 转向与执行移动
        MovementSync.Instance.lookAt(currentWaypoint);

        // 自动跳跃逻辑：路点比当前高且水平距离近
        if (currentWaypoint.y > pos.y + 0.1 && dist2D < 1.2) {
            MovementSync.Instance.jump();
        }

        if (dist2D > 0) {
            Vector3d dir = new Vector3d(dx, 0, dz).normalize();
            double speed = MovementSync.movementSpeed;
            Vector3d currentVel = MovementSync.Instance.velocity.get();
            double currentYVel = currentVel != null ? currentVel.y : 0;
            
            // 每一帧强制维持速度，确保移动平滑
            MovementSync.Instance.velocity.set(new Vector3d(dir.x * speed, currentYVel, dir.z * speed));
        }
    }

    private void stopHorizontalMovement() {
        Vector3d vel = MovementSync.Instance.velocity.get();
        if (vel != null) {
            MovementSync.Instance.velocity.set(new Vector3d(0, vel.y, 0));
        }
    }

    @Override
    public long getTime() {
        return 60000; // 最大寻路持续 60 秒
    }

    @Override
    public void onStop() {
        logger.info("[DynamicPathMovement] 寻路任务已停止。");
        stopHorizontalMovement();
    }
}
