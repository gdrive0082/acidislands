package id.alvarennation.acidIsland.hooks;

import org.bukkit.Bukkit;
import org.geysermc.floodgate.api.FloodgateApi;
import java.util.UUID;

public class FloodgateHook {

    private static boolean enabled = false;

    public static void setup() {
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            enabled = true;
        }
    }

    public static boolean isBedrockPlayer(UUID uuid) {
        if (!enabled) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (NoClassDefFoundError | Exception e) {
            return false;
        }
    }
}
