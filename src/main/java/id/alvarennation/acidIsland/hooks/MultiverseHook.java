package id.alvarennation.acidIsland.hooks;

import org.bukkit.Bukkit;

public class MultiverseHook {

    public static void registerWorld(String worldName) {
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + worldName + " normal -g AcidIsland");
            } catch (Exception ignored) {}
        }
    }
}
