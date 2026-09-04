package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import xin.bbtt.Block.BlockState;
import xin.bbtt.Entity.Entity;

final class EntityLookTargetToolTest {
    private static final BlockState AIR = new BlockState(
        "minecraft:air", 0, java.util.Map.of(), "empty", 0.0, false, "AIR");
    private static final BlockState STONE = new BlockState(
        "minecraft:stone", 1, java.util.Map.of(), "block", 1.5, true, "STONE");

    @Test
    void returnsFirstBlockHitFaceAndPreviousAirCell() {
        Entity entity = new Entity(
            7, UUID.randomUUID(), EntityType.PLAYER,
            0.5, 64.0, 0.5, 0.0f, 0.0f, 0.0f, new Vector3d());
        PerceptionTools tools = new PerceptionTools(
            (x, y, z) -> x == 0 && y == 65 && z == 3 ? STONE : AIR,
            () -> new Vector3d(),
            Set::of,
            id -> id == 7 ? entity : null
        );

        String result = tools.getEntityLookTarget(7, 8.0);

        assertTrue(result.contains("hit=(0,65,3)"), result);
        assertTrue(result.contains("block=minecraft:stone"), result);
        assertTrue(result.contains("face=NORTH"), result);
        assertTrue(result.contains("previous=(0,65,2)"), result);
    }

    @Test
    void reportsMissingEntityWithoutGuessing() {
        PerceptionTools tools = new PerceptionTools(
            (x, y, z) -> AIR, Vector3d::new, Set::of, id -> null);

        String result = tools.getEntityLookTarget(999, 8.0);

        assertTrue(result.contains("未找到实体 ID 999"), result);
    }
}
