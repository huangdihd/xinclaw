package xin.agent;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class Inspect {
    public static void main(String[] args) throws Exception {
        String[] classNames = {
            "org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket",
            "org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket",
            "org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType",
            "org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction",
            "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket"
        };
        
        for (String name : classNames) {
            System.out.println("Constructors and Methods in " + name + ":");
            try {
                Class<?> c = Class.forName(name);
                for (Constructor<?> cons : c.getDeclaredConstructors()) {
                    System.out.println("  " + cons.toString());
                }
                for (Method m : c.getDeclaredMethods()) {
                    System.out.println("  " + m.toString());
                }
            } catch (Exception e) {
                System.out.println("  Not found: " + e.getMessage());
            }
        }
    }
}
