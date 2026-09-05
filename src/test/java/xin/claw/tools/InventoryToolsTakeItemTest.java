package xin.claw.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xin.bbtt.mcbot.Bot;
import xin.claw.XinClawPlugin;
import xin.claw.trackers.InventoryTracker;

final class InventoryToolsTakeItemTest {
    @AfterEach
    void reset() throws Exception {
        setBotField("session", null);
        XinClawPlugin.INSTANCE = null;
    }

    @Test
    void shiftClicksAStackFromAnOpenExternalContainer() throws Exception {
        List<Object> packets = new ArrayList<>();
        setBotField("session", Proxy.newProxyInstance(
            ClientSession.class.getClassLoader(), new Class<?>[]{ClientSession.class},
            (proxy, method, args) -> {
                if (method.getName().equals("send") && args != null && args.length > 0) packets.add(args[0]);
                Class<?> type = method.getReturnType();
                if (!type.isPrimitive()) return null;
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == float.class) return 0.0f;
                if (type == double.class) return 0.0d;
                if (type == byte.class) return (byte) 0;
                if (type == short.class) return (short) 0;
                if (type == char.class) return '\0';
                return null;
            }
        ));
        XinClawPlugin plugin = new XinClawPlugin();
        XinClawPlugin.INSTANCE = plugin;
        plugin.inventoryTracker = new InventoryTracker();
        setTrackerField(plugin.inventoryTracker, "currentContainerId", 5);
        setTrackerField(plugin.inventoryTracker, "currentStateId", 17);
        @SuppressWarnings("unchecked")
        Map<Integer, ItemStack[]> containers = (ConcurrentHashMap<Integer, ItemStack[]>)
            trackerField(plugin.inventoryTracker, "containers");
        ItemStack[] slots = new ItemStack[63];
        slots[0] = new ItemStack(1, 32);
        containers.put(5, slots);

        String result = new InventoryTools().takeContainerItem(0);

        assertEquals(1, packets.size());
        ServerboundContainerClickPacket packet =
            (ServerboundContainerClickPacket) packets.get(0);
        assertEquals(5, packet.getContainerId());
        assertEquals(17, packet.getStateId());
        assertEquals(0, packet.getSlot());
        assertEquals(ContainerActionType.SHIFT_CLICK_ITEM, packet.getAction());
        assertEquals(ShiftClickItemAction.LEFT_CLICK, packet.getParam());
        assertTrue(result.contains("已请求"), result);
        assertTrue(result.contains("x32"), result);
    }

    @Test
    void shiftClicksAPlayerInventoryStackIntoTheOpenContainer() throws Exception {
        List<Object> packets = new ArrayList<>();
        setBotField("session", Proxy.newProxyInstance(
            ClientSession.class.getClassLoader(), new Class<?>[]{ClientSession.class},
            (proxy, method, args) -> {
                if (method.getName().equals("send") && args != null && args.length > 0) packets.add(args[0]);
                Class<?> type = method.getReturnType();
                if (!type.isPrimitive()) return null;
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == float.class) return 0.0f;
                if (type == double.class) return 0.0d;
                if (type == byte.class) return (byte) 0;
                if (type == short.class) return (short) 0;
                if (type == char.class) return '\0';
                return null;
            }
        ));
        XinClawPlugin plugin = new XinClawPlugin();
        XinClawPlugin.INSTANCE = plugin;
        plugin.inventoryTracker = new InventoryTracker();
        setTrackerField(plugin.inventoryTracker, "currentContainerId", 5);
        setTrackerField(plugin.inventoryTracker, "currentStateId", 18);
        @SuppressWarnings("unchecked")
        Map<Integer, ItemStack[]> containers = (ConcurrentHashMap<Integer, ItemStack[]>)
            trackerField(plugin.inventoryTracker, "containers");
        ItemStack[] slots = new ItemStack[63];
        slots[27] = new ItemStack(1, 16);
        containers.put(5, slots);

        String result = new InventoryTools().putInventoryItemIntoContainer(27);

        assertEquals(1, packets.size());
        ServerboundContainerClickPacket packet =
            (ServerboundContainerClickPacket) packets.get(0);
        assertEquals(27, packet.getSlot());
        assertEquals(ContainerActionType.SHIFT_CLICK_ITEM, packet.getAction());
        assertEquals(ShiftClickItemAction.LEFT_CLICK, packet.getParam());
        assertTrue(result.contains("已请求"), result);
        assertTrue(result.contains("x16"), result);
    }

    private static Object trackerField(InventoryTracker tracker, String name) throws Exception {
        Field field = InventoryTracker.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(tracker);
    }

    private static void setTrackerField(InventoryTracker tracker, String name, Object value) throws Exception {
        Field field = InventoryTracker.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(tracker, value);
    }

    private static void setBotField(String name, Object value) throws Exception {
        Field field = Bot.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(Bot.INSTANCE, value);
    }
}
