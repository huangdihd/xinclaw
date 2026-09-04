package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xin.bbtt.Entity.Entity;
import xin.bbtt.MovementSync;

final class EntityFacingToolTest {
    @AfterEach
    void resetSingleton() {
        MovementSync.INSTANCE = null;
    }

    @Test
    void facesEntityBodyCentreImmediately() {
        MovementSync movementSync = new MovementSync();
        movementSync.position.set(new Vector3d(0.5, 64.0, 0.5));
        Entity target = new Entity(
            7, UUID.randomUUID(), EntityType.PLAYER,
            3.5, 64.0, 0.5, 0.0f, 0.0f, 0.0f, new Vector3d());
        ActionTools tools = new ActionTools(id -> id == 7 ? target : null);

        String result = tools.faceEntity(7);

        assertEquals(-90.0f, movementSync.yaw.get(), 0.001f);
        assertEquals(64.9, ActionTools.entityAimPoint(target).y, 0.001);
        double expectedPitch = Math.toDegrees(Math.atan2(65.62 - 64.9, 3.0));
        assertEquals(expectedPitch, movementSync.pitch.get(), 0.001);
        assertTrue(result.contains("entity_id=7"), result);
        assertTrue(result.contains("yaw=-90.0"), result);
    }

    @Test
    void agentCanSetInspectAndClearPersistentTargets() {
        MovementSync movementSync = new MovementSync();
        Entity target = new Entity(
            7, UUID.randomUUID(), EntityType.PLAYER,
            3.5, 64.0, 0.5, 0.0f, 0.0f, 0.0f, new Vector3d());
        ActionTools tools = new ActionTools(id -> id == 7 ? target : null);

        assertTrue(tools.setBlockGazeTarget(1, 65, 2).contains("block=(1,65,2)"));
        assertEquals("block=(1,65,2)", tools.getGazeTarget());
        assertTrue(tools.setEntityGazeTarget(7).contains("entity_id=7"));
        assertTrue(tools.getGazeTarget().contains("entity_id=7"));
        tools.clearGazeTarget();
        assertEquals("none", tools.getGazeTarget());
    }
}
