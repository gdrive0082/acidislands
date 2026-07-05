package id.alvarennation.acidIsland.quest;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.hooks.VaultHook;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestManager {

    private final AcidIsland plugin;

    public QuestManager(AcidIsland plugin) {
        this.plugin = plugin;
    }

    public List<String> getQuestIds() {
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("quests");
        if (section == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (section.getConfigurationSection(key) != null) {
                ids.add(key);
            }
        }
        return ids;
    }

    public String getDisplayName(String questId) {
        return plugin.getConfigManager().getConfig().getString("quests." + questId + ".display-name", questId);
    }

    public List<String> getDescription(String questId) {
        return plugin.getConfigManager().getConfig().getStringList("quests." + questId + ".description");
    }

    public Material getIcon(String questId) {
        String raw = plugin.getConfigManager().getConfig().getString("quests." + questId + ".icon", "PAPER");
        Material material = Material.matchMaterial(raw);
        return material == null ? Material.PAPER : material;
    }

    public double getRewardMoney(String questId) {
        return plugin.getConfigManager().getConfig().getDouble("quests." + questId + ".reward-money", 0.0);
    }

    public boolean canClaim(Island island, String questId) {
        if (island == null || island.hasCompletedQuest(questId)) {
            return false;
        }
        return meetsRequirements(island, questId);
    }

    public boolean meetsRequirements(Island island, String questId) {
        ConfigurationSection req = plugin.getConfigManager().getConfig().getConfigurationSection("quests." + questId + ".requirements");
        if (req == null) {
            return true;
        }

        for (String key : req.getKeys(false)) {
            if (!matchesRequirement(island, key, req)) {
                return false;
            }
        }
        return true;
    }

    public ClaimResult claim(Player player, Island island, String questId) {
        if (island == null) {
            return ClaimResult.NO_ISLAND;
        }
        if (island.hasCompletedQuest(questId)) {
            return ClaimResult.ALREADY_COMPLETED;
        }
        if (!getQuestIds().contains(questId)) {
            return ClaimResult.NOT_FOUND;
        }
        if (!meetsRequirements(island, questId)) {
            return ClaimResult.REQUIREMENTS_NOT_MET;
        }

        double rewardMoney = getRewardMoney(questId);
        if (rewardMoney > 0 && VaultHook.hasEconomy()) {
            VaultHook.getEconomy().depositPlayer(player, rewardMoney);
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
        for (String command : plugin.getConfigManager().getConfig().getStringList("quests." + questId + ".reward-commands")) {
            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{owner}", owner.getName() == null ? player.getName() : owner.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        island.completeQuest(questId);
        plugin.getIslandManager().saveData();
        return ClaimResult.CLAIMED;
    }

    public List<String> getRequirementLore(Island island, String questId) {
        ConfigurationSection req = plugin.getConfigManager().getConfig().getConfigurationSection("quests." + questId + ".requirements");
        if (req == null) {
            return List.of("&7Requirement: &aTidak ada");
        }

        List<String> lines = new ArrayList<>();
        for (String key : req.getKeys(false)) {
            boolean ok = matchesRequirement(island, key, req);
            lines.add((ok ? "&a[OK] " : "&c[NO] ") + describeRequirement(island, key, req));
        }
        return lines;
    }

    private boolean matchesRequirement(Island island, String key, ConfigurationSection req) {
        return switch (key) {
            case "created" -> (island != null) == req.getBoolean(key, true);
            case "border-level" -> island.getLevel("border") >= req.getInt(key);
            case "generator-level" -> island.getLevel("generator") >= req.getInt(key);
            case "bank-balance" -> island.getBankBalance() >= req.getDouble(key);
            case "members" -> island.getMembers().size() >= req.getInt(key);
            case "theme" -> island.getTheme().equalsIgnoreCase(req.getString(key, ""));
            case "island-level" -> plugin.getIslandManager().getIslandLevel(island, false) >= req.getInt(key);
            default -> true;
        };
    }

    private String describeRequirement(Island island, String key, ConfigurationSection req) {
        return switch (key) {
            case "created" -> "Punya island";
            case "border-level" -> "Border tier " + req.getInt(key) + " (sekarang " + island.getLevel("border") + ")";
            case "generator-level" -> "Generator tier " + req.getInt(key) + " (sekarang " + island.getLevel("generator") + ")";
            case "bank-balance" -> "Saldo bank $" + req.getDouble(key) + " (sekarang $" + island.getBankBalance() + ")";
            case "members" -> "Minimal " + req.getInt(key) + " member (sekarang " + island.getMembers().size() + ")";
            case "theme" -> "Theme " + req.getString(key, "-") + " (sekarang " + island.getTheme() + ")";
            case "island-level" -> "Island level " + req.getInt(key) + " (sekarang " + plugin.getIslandManager().getIslandLevel(island, false) + ")";
            default -> key + ": " + req.getString(key);
        };
    }

    public enum ClaimResult {
        CLAIMED,
        NO_ISLAND,
        NOT_FOUND,
        ALREADY_COMPLETED,
        REQUIREMENTS_NOT_MET
    }
}
