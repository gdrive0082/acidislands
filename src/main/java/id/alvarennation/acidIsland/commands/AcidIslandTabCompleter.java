package id.alvarennation.acidIsland.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AcidIslandTabCompleter implements TabCompleter {

    private final id.alvarennation.acidIsland.AcidIsland plugin;

    public AcidIslandTabCompleter(id.alvarennation.acidIsland.AcidIsland plugin) {
        this.plugin = plugin;
    }

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "start", "home", "sethome", "setting", "upgrade", 
            "vault", "bank", "invite", "accept", "reject", "kick", 
            "leave", "delete", "lobby"
    );

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> available = new ArrayList<>(SUBCOMMANDS);
            completions = available.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            if (sub.equals("invite") || sub.equals("kick")) {
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> !name.equals(player.getName()))
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            } else if (sub.equals("bank")) {
                completions = Arrays.asList("deposit", "withdraw").stream()
                        .filter(s -> s.startsWith(input))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String bankSub = args[1].toLowerCase();
            if (sub.equals("bank") && (bankSub.equals("deposit") || bankSub.equals("withdraw"))) {
                String input = args[2].toLowerCase();
                completions = Arrays.asList("100", "1000", "5000", "10000").stream()
                        .filter(s -> s.startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}
