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

package xin.agent.utils;

import org.joml.Vector3d;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import xin.bbtt.MovementSync;
import xin.bbtt.mcbot.Bot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RotationUtils {
    private static final Logger logger = LoggerFactory.getLogger(RotationUtils.class);

    public static void instantLookAt(Vector3d target) {
        if (MovementSync.Instance == null || Bot.Instance == null || Bot.Instance.getSession() == null) return;

        Vector3d pos = MovementSync.Instance.position.get();
        if (pos == null) return;

        Vector3d headPos = MovementSync.Instance.getHeadPosition();
        Vector3d delta = new Vector3d(target).sub(headPos);

        double dx = delta.x;
        double dy = delta.y;
        double dz = delta.z;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // Update local state
        MovementSync.Instance.yaw.set(targetYaw);
        MovementSync.Instance.pitch.set(targetPitch);

        logger.debug("[Rotation] Instant look at ({}, {}, {}), Yaw: {}, Pitch: {}", target.x, target.y, target.z, targetYaw, targetPitch);

        // Sync to server using the full PosRot packet which is more robust
        Bot.Instance.getSession().send(new ServerboundMovePlayerPosRotPacket(
                MovementSync.Instance.onGround.get(),
                false, // horizontalCollision
                pos.x,
                pos.y,
                pos.z,
                targetYaw,
                targetPitch
        ));
    }
}
