import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class InspectMc2 {
    public static void main(String[] args) throws Exception {
        URL[] urls = {new URL("file:///Users/h2028176/.m2/repository/org/geysermc/mcprotocollib/protocol/1.21-SNAPSHOT/protocol-1.21-SNAPSHOT.jar")};
        URLClassLoader cl = new URLClassLoader(urls);
        
        String[] classNames = {
            "org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket",
            "org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket",
            "org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType",
            "org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction"
        };
        
        for (String name : classNames) {
            System.out.println("Methods in " + name + ":");
            try {
                Class<?> c = cl.loadClass(name);
                for (Method m : c.getDeclaredMethods()) {
                    System.out.println("  " + m.toString());
                }
            } catch (Exception e) {
                System.out.println("  Not found: " + e.getMessage());
            }
        }
    }
}
