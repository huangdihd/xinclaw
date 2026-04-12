import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class InspectMc {
    public static void main(String[] args) throws Exception {
        URL[] urls = {new URL("file:///Users/h2028176/.m2/repository/org/geysermc/mcprotocollib/protocol/1.21-SNAPSHOT/protocol-1.21-SNAPSHOT.jar")};
        URLClassLoader cl = new URLClassLoader(urls);
        
        String[] classNames = {
            "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket",
            "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket",
            "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket",
            "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket"
        };
        
        for (String name : classNames) {
            System.out.println("Constructors for " + name + ":");
            try {
                Class<?> c = cl.loadClass(name);
                for (Constructor<?> m : c.getDeclaredConstructors()) {
                    System.out.println("  " + m.toString());
                }
            } catch (Exception e) {
                System.out.println("  Not found.");
            }
        }
    }
}
