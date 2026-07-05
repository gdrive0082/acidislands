package id.alvarennation.acidIsland.commands;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class AcidIslandTabCompleter implements TabCompleter {

    private final AcidIsland plugin;

    public AcidIslandTabCompleter(AcidIsland plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(getVisibleSubcommands(sender), args[0]);
        }

        String sub = AcidIslandCommandSpec.normalize(args[0]);
        if (AcidIslandCommandSpec.isAdminOnly(sub) && !hasAdminPermission(sender)) {
            return List.of();
        }

        if (args.length == 2) {
            return switch (sub) {
                case "invite" -> onlinePlayerNames(sender, args[1]);
                case "kick", "role" -> islandMemberNames(sender, args[1]);
                case "bank" -> filter(AcidIslandCommandSpec.BANK_ACTIONS, args[1]);
                case "accept" -> filter(List.of("confirm"), args[1]);
                case "quest" -> filter(AcidIslandCommandSpec.QUEST_ACTIONS, args[1]);
                case "level" -> filter(AcidIslandCommandSpec.LEVEL_ACTIONS, args[1]);
                case "story" -> filter(AcidIslandCommandSpec.STORY_ACTIONS, args[1]);
                case "theme" -> filter(getThemeIds(), args[1]);
                case "admin" -> filter(AcidIslandCommandSpec.ADMIN_ACTIONS, args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            if (sub.equals("bank") && (args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("withdraw"))) {
                return filter(AcidIslandCommandSpec.BANK_AMOUNTS, args[2]);
            }
            if (sub.equals("quest")) {
                return args[1].equalsIgnoreCase("claim") ? filter(plugin.getQuestManager().getQuestIds(), args[2]) : List.of();
            }
            if (sub.equals("story")) {
                return args[1].equalsIgnoreCase("start") ? filter(getStoryConversationIds(), args[2]) : List.of();
            }
            if (sub.equals("role")) {
                return filter(AcidIslandCommandSpec.ROLE_NAMES, args[2]);
            }
            if (sub.equals("admin") && args[1].equalsIgnoreCase("story")) {
                return filter(AcidIslandCommandSpec.ADMIN_STORY_ACTIONS, args[2]);
            }
            if (sub.equals("admin")) {
                return onlinePlayerNames(sender, args[2]);
            }
        }

        if (args.length == 4 && sub.equals("admin") && args[1].equalsIgnoreCase("story")) {
            return onlinePlayerNames(sender, args[3]);
        }

        if (args.length == 5 && sub.equals("admin") && args[1].equalsIgnoreCase("story")
                && (args[2].equalsIgnoreCase("set") || args[2].equalsIgnoreCase("add"))) {
            return filter(AcidIslandCommandSpec.STORY_STAGE_SAMPLES, args[4]);
        }

        return List.of();
    }

    private List<String> onlinePlayerNames(CommandSender sender, String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !(sender instanceof Player player) || !name.equals(player.getName()))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> islandMemberNames(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island == null) {
            return List.of();
        }

        return island.getMembers().stream()
                .map(uuid -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        return online.getName();
                    }
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                    return offline.getName() == null ? uuid.toString() : offline.getName();
                })
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> getVisibleSubcommands(CommandSender sender) {
        if (hasAdminPermission(sender)) {
            return AcidIslandCommandSpec.ALL_SUBCOMMANDS;
        }
        return AcidIslandCommandSpec.PLAYER_SUBCOMMANDS;
    }

    private List<String> getThemeIds() {
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("themes");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .filter(key -> section.getConfigurationSection(key) != null)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> getStoryConversationIds() {
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("integrations.conversecraft.story-stage-conversations");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("acidisland.admin");
    }
}
