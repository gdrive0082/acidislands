package id.alvarennation.acidIsland.commands;

import id.alvarennation.acidIsland.AcidIsland;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AcidIslandTabCompleter implements TabCompleter {

    private final AcidIsland plugin;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "start", "home", "sethome", "setting", "upgrade",
            "vault", "bank", "invite", "accept", "reject", "kick",
            "leave", "delete", "lobby", "info", "quest", "level",
            "top", "theme", "role", "setlobby", "reload", "admin"
    );

    public AcidIslandTabCompleter(AcidIsland plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "invite", "kick", "role" -> onlinePlayerNames(sender, args[1]);
                case "bank" -> filter(List.of("deposit", "withdraw"), args[1]);
                case "accept" -> filter(List.of("confirm"), args[1]);
                case "quest", "quests" -> filter(List.of("claim"), args[1]);
                case "level" -> filter(List.of("refresh"), args[1]);
                case "theme", "themes", "biome" -> filter(getThemeIds(), args[1]);
                case "admin" -> filter(List.of("delete", "reset", "tp", "story"), args[1]);
                default -> new ArrayList<>();
            };
        }

        if (args.length == 3) {
            if (sub.equals("bank") && (args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("withdraw"))) {
                return filter(List.of("100", "1000", "5000", "10000"), args[2]);
            }
            if (sub.equals("quest") || sub.equals("quests")) {
                return args[1].equalsIgnoreCase("claim") ? filter(plugin.getQuestManager().getQuestIds(), args[2]) : new ArrayList<>();
            }
            if (sub.equals("role") || sub.equals("roles")) {
                return filter(List.of("member", "trusted", "co_owner"), args[2]);
            }
            if (sub.equals("admin") && args[1].equalsIgnoreCase("story")) {
                return filter(List.of("set", "get", "add"), args[2]);
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
            return filter(List.of("0", "1", "2", "3", "4", "5"), args[4]);
        }

        return new ArrayList<>();
    }

    private List<String> onlinePlayerNames(CommandSender sender, String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !(sender instanceof Player player) || !name.equals(player.getName()))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private List<String> getThemeIds() {
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("themes");
        List<String> ids = new ArrayList<>();
        if (section == null) {
            return ids;
        }
        for (String key : section.getKeys(false)) {
            if (section.getConfigurationSection(key) != null) {
                ids.add(key);
            }
        }
        return ids;
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
