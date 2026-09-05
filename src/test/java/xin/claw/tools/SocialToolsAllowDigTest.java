package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import xin.bbtt.MovementSync;

final class SocialToolsAllowDigTest {
    @Test
    void followRepathExplicitlyDisablesDigging() {
        RecordingMovementSync movementSync = new RecordingMovementSync();

        SocialTools.triggerFollowRepath(movementSync, 42);

        assertEquals(Boolean.FALSE, movementSync.allowDigging,
            "following must pass an explicit no-dig permission");
        assertEquals(42, movementSync.entityId);
    }

    private static final class RecordingMovementSync extends MovementSync {
        private Boolean allowDigging;
        private int entityId = -1;

        @Override
        public void startFollowingNavigation(int entityId, double keepDistance, boolean allowDigging) {
            this.entityId = entityId;
            this.allowDigging = allowDigging;
        }
    }
}
