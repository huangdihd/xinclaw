package xin.agent;

import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.MovementSync;
import xin.bbtt.movement.Movement;

public class TargetMovement extends Movement {
    private static final Logger logger = LoggerFactory.getLogger(TargetMovement.class);
    private final Vector3d target;
    private final long durationMs;
    private final double ACCEPTANCE_RADIUS = 0.3;

    public TargetMovement(Vector3d target, long durationMs) {
        this.target = target;
        this.durationMs = durationMs;
    }

    @Override
    public void init() {
        logger.info("[TargetMovement] 开始移动，目标: ({}, {}, {}), 预计耗时: {}ms", target.x, target.y, target.z, durationMs);
        MovementSync.Instance.lookAt(target);
    }

    @Override
    public void onTick() {
        if (MovementSync.Instance == null) return;
        
        Vector3d pos = MovementSync.Instance.position.get();
        if (pos == null) return;

        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double dist2D = Math.sqrt(dx * dx + dz * dz);

        if (dist2D < ACCEPTANCE_RADIUS) {
            stopHorizontalMovement();
            return;
        }

        // 转向目标
        MovementSync.Instance.lookAt(target);

        // 计算方向
        Vector3d dir = new Vector3d(dx, 0, dz).normalize();
        
        // 补偿物理引擎的摩擦力 (地面摩擦 0.91 * 输入摩擦 0.98 = 0.8918)
        // 为了达到目标速度，我们需要设置一个稍大的初始速度
        double compensationFactor = 1.0 / (0.91 * 0.98); 
        double speed = MovementSync.movementSpeed * compensationFactor;
        
        Vector3d currentVel = MovementSync.Instance.velocity.get();
        double currentYVel = currentVel != null ? currentVel.y : 0;
        
        // 设置新速度
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
        return durationMs;
    }

    @Override
    public void onStop() {
        logger.info("[TargetMovement] 移动结束。");
        stopHorizontalMovement();
    }
}
