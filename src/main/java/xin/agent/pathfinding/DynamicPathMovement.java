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
            logger.info("[DynamicPathMovement] 寻路成功，重新规划了 {} 个节点。", this.currentPath.size());
        }
    }

    @Override
    public void onTick() {
        if (MovementSync.Instance == null) return;
        Vector3d pos = MovementSync.Instance.position.get();
        if (pos == null) return;

        // 如果距离最终目标很近，就停止
        if (pos.distanceSquared(finalTarget) < 0.5) {
            stopHorizontalMovement();
            return;
        }

        // 如果没有路径，尝试重新规划（每2秒最多一次）
        if (currentPath == null || currentPath.isEmpty() || currentWaypointIndex >= currentPath.size()) {
            if (System.currentTimeMillis() - lastRecalculateTime > 2000) {
                recalculatePath();
            }
            if (currentPath == null || currentPath.isEmpty()) {
                stopHorizontalMovement();
                return;
            }
        }

        Vector3d currentWaypoint = currentPath.get(currentWaypointIndex);

        // 检查是否卡住 (位置一直没变)
        if (lastPos != null && pos.distanceSquared(lastPos) < 0.001) {
            stuckTicks++;
            // 自动跳跃尝试解卡
            if (stuckTicks == 5) {
                MovementSync.Instance.jump();
            }
            if (stuckTicks > 20) { // 卡住超过 1 秒，重新寻路
                logger.warn("[DynamicPathMovement] 似乎卡住了，重新规划路径...");
                stuckTicks = 0;
                recalculatePath();
                return;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = new Vector3d(pos);

        // 如果到达当前路点，前往下一个
        double dx = currentWaypoint.x - pos.x;
        double dz = currentWaypoint.z - pos.z;
        double dist2D = Math.sqrt(dx * dx + dz * dz);

        if (dist2D < 0.4) {
            currentWaypointIndex++;
            if (currentWaypointIndex >= currentPath.size()) {
                // 到达最后一个路点（可能是目标附近）
                stopHorizontalMovement();
                return;
            }
            currentWaypoint = currentPath.get(currentWaypointIndex);
            dx = currentWaypoint.x - pos.x;
            dz = currentWaypoint.z - pos.z;
            dist2D = Math.sqrt(dx * dx + dz * dz);
        }

        // 转向并移动
        xin.agent.utils.RotationUtils.instantLookAt(currentWaypoint);

        // 如果需要爬坡且在边缘，自动跳跃
        if (currentWaypoint.y > pos.y + 0.5 && dist2D < 1.0) {
            MovementSync.Instance.jump();
        }

        Vector3d dir = new Vector3d(dx, 0, dz).normalize();
        double compensationFactor = 1.0 / (0.91 * 0.98); 
        double speed = MovementSync.movementSpeed * compensationFactor;
        
        Vector3d currentVel = MovementSync.Instance.velocity.get();
        double currentYVel = currentVel != null ? currentVel.y : 0;
        
        Vector3d newVel = new Vector3d(dir.x * speed, currentYVel, dir.z * speed);
        MovementSync.Instance.velocity.set(newVel);
    }

    private void stopHorizontalMovement() {
        Vector3d vel = MovementSync.Instance.velocity.get();
        if (vel != null) {
            MovementSync.Instance.velocity.set(new Vector3d(0, vel.y, 0));
        }
    }

    @Override
    public long getTime() {
        // 这个移动任务是持续的，直到我们取消它或到达目标
        return 60000; // 最多走 60 秒
    }

    @Override
    public void onStop() {
        logger.info("[DynamicPathMovement] 动态寻路任务结束。");
        stopHorizontalMovement();
    }
}
