package id.alvarennation.acidIsland.hooks;

import org.bukkit.Bukkit;
import java.util.logging.Logger;

public class ModelEngineHook {

    private static boolean enabled = false;

    public static void setup(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
            enabled = true;
            logger.info("Hooked into ModelEngine successfully.");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
