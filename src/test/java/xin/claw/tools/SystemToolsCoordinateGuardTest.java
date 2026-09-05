package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import xin.claw.CoordinateRange;
import xin.claw.PluginConfig;

final class SystemToolsCoordinateGuardTest {
    private static final CoordinateRange SECRET_BASE = CoordinateRange.parse(
        "100,40,-300,200,100,-200").orElseThrow();

    @Test
    void blocksLabeledAndTupleCoordinatesInsideConfiguredRange() {
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "base is at x=150, y=64, z=-250", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "meet at (150, 64, -250)", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "150 64 -250", SECRET_BASE));
    }

    @Test
    void blocksCoordinatesThatOnlyDiscloseXAndZ() {
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "base x=150 z=-250", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "meet at (150, -250)", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "150 -250", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "base coordinates 150 -250", SECRET_BASE));
        assertFalse(SystemTools.containsForbiddenCoordinates(
            "score 150 64 -250", SECRET_BASE));
        assertFalse(SystemTools.containsForbiddenCoordinates(
            "x=99 z=-250", SECRET_BASE));
    }

    @Test
    void recognizesEquivalentCoordinateFormats() {
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "z=-250 y=64 x=150", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "1.5e2;64;-2.5e2", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "150/-250", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "x=150 z=-250 x=99", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "go to 150 64 -250", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "I am at 150 64 -250", SECRET_BASE));
        assertTrue(SystemTools.containsForbiddenCoordinates(
            "x 150 y 64 z -250", SECRET_BASE));
    }

    @Test
    void allowsCoordinatesOutsideConfiguredRangeAndUnrelatedNumbers() {
        assertFalse(SystemTools.containsForbiddenCoordinates(
            "meet at (99, 64, -250)", SECRET_BASE));
        assertFalse(SystemTools.containsForbiddenCoordinates(
            "version 12.3.45", SECRET_BASE));
    }

    @Test
    void configuredRangeUsesInclusiveEndpointsAndCanBeDisabled() {
        assertTrue(SECRET_BASE.contains(100, 40, -300));
        assertTrue(SECRET_BASE.contains(200, 100, -200));
        assertTrue(CoordinateRange.parse("").isEmpty());
    }

    @Test
    void publicChatBoundaryRejectsProtectedCoordinatesBeforeBotAccess() {
        CoordinateRange original = PluginConfig.publicChatForbiddenCoordinateRange;
        try {
            PluginConfig.publicChatForbiddenCoordinateRange = SECRET_BASE;
            assertEquals(
                "发送失败：消息包含配置为禁止公开的坐标范围。",
                new SystemTools(false).sendChatMessage("x=150 y=64 z=-250")
            );
        } finally {
            PluginConfig.publicChatForbiddenCoordinateRange = original;
        }
    }

    @Test
    void publicSayCommandCannotBypassTheCoordinateGuard() {
        CoordinateRange original = PluginConfig.publicChatForbiddenCoordinateRange;
        try {
            PluginConfig.publicChatForbiddenCoordinateRange = SECRET_BASE;
            assertEquals(
                "执行失败：公屏指令包含配置为禁止公开的坐标范围。",
                new SystemTools(true).sendCommand("say meet at 150 64 -250")
            );
        } finally {
            PluginConfig.publicChatForbiddenCoordinateRange = original;
        }
    }

    @Test
    void alternatePublicCommandsCannotBypassTheCoordinateGuard() {
        CoordinateRange original = PluginConfig.publicChatForbiddenCoordinateRange;
        try {
            PluginConfig.publicChatForbiddenCoordinateRange = SECRET_BASE;
            SystemTools tools = new SystemTools(true);
            assertEquals(
                "执行失败：公屏指令包含配置为禁止公开的坐标范围。",
                tools.sendCommand("minecraft:say\tx=150 z=-250")
            );
            assertEquals(
                "执行失败：公屏指令包含配置为禁止公开的坐标范围。",
                tools.sendCommand("execute as @s run say 150 64 -250")
            );
            assertEquals(
                "执行失败：公屏指令包含配置为禁止公开的坐标范围。",
                tools.sendCommand("tellraw @a {\"text\":\"150 -250\"}")
            );
            assertEquals(
                "执行失败：公屏指令包含配置为禁止公开的坐标范围。",
                tools.sendCommand(
                    "tellraw @a {\"text\":\"150\",\"extra\":[{\"text\":\"-250\"}]}"
                )
            );
            assertEquals(
                "执行失败：公屏指令包含配置为禁止公开的坐标范围。",
                tools.sendCommand(
                    "tellraw @a [{\"text\":\"z=\"},{\"text\":\"-250\"},"
                        + "{\"text\":\" x=\"},{\"text\":\"150\"}]"
                )
            );
        } finally {
            PluginConfig.publicChatForbiddenCoordinateRange = original;
        }
    }
}
