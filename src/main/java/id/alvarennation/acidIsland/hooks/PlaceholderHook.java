package id.alvarennation.acidIsland.hooks;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderHook {

    public static void setup(AcidIsland plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AcidIslandExpansion(plugin).register();
            plugin.getLogger().info("Registered PlaceholderAPI Expansion (%acidisland_*%).");
        }
    }

    private static class AcidIslandExpansion extends PlaceholderExpansion {

        private final AcidIsland plugin;

        public AcidIslandExpansion(AcidIsland plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "acidisland";
        }

        @Override
        public @NotNull String getAuthor() {
            return "alvarennation";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            if (player == null) return "";

            Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());

            switch (params.toLowerCase()) {
                case "has_island" -> {
                    return island != null ? "true" : "false";
                }
                case "owner" -> {
                    if (island == null) return "-";
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
                    return owner.getName() != null ? owner.getName() : "-";
                }
                case "members_count" -> {
                    return island != null ? String.valueOf(island.getMembers().size()) : "0";
                }
                case "border_size" -> {
                    if (island == null) return "0";
                    int level = island.getLevel("border");
                    return String.valueOf(plugin.getConfigManager().getConfig().getInt("upgrades.border." + level + ".size", 50));
                }
                case "bank_balance" -> {
                    return island != null ? String.valueOf(island.getBankBalance()) : "0.0";
                }
                case "vault_level" -> {
                    return island != null ? String.valueOf(island.getLevel("vault")) : "1";
                }
                case "generator_level" -> {
                    return island != null ? String.valueOf(island.getLevel("generator")) : "1";
                }
                case "island_level" -> {
                    return island != null ? String.valueOf(plugin.getIslandManager().getIslandLevel(island, false)) : "0";
                }
                case "island_value" -> {
                    return island != null ? String.valueOf(plugin.getIslandManager().getIslandValue(island, false)) : "0";
                }
                case "theme" -> {
                    return island != null ? island.getTheme() : "-";
                }
                case "role" -> {
                    return island != null ? island.getRole(player.getUniqueId()).getDisplayName() : "Visitor";
                }
                case "quests_completed" -> {
                    return island != null ? String.valueOf(island.getCompletedQuests().size()) : "0";
                }
            }

            return null;
        }
    }
}
