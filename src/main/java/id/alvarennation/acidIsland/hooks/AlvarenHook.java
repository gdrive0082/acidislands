package id.alvarennation.acidIsland.hooks;

import org.bukkit.Bukkit;
import java.util.logging.Logger;

public class AlvarenHook {

    private static boolean coreEnabled = false;
    private static boolean ecoEnabled = false;

    public static void setup(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("AlvarenCore") != null) {
            coreEnabled = true;
            logger.info("Hooked into AlvarenCore successfully.");
        }
        if (Bukkit.getPluginManager().getPlugin("AlvarenEconomy") != null) {
            ecoEnabled = true;
            logger.info("Hooked into AlvarenEconomy successfully.");
        }
    }

    public static boolean isCoreEnabled() {
        return coreEnabled;
    }

    public static boolean isEcoEnabled() {
        return ecoEnabled;
    }
}
