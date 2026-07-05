package id.alvarennation.acidIsland.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AcidIslandCommandSpec {

    static final List<String> PLAYER_SUBCOMMANDS = List.of(
            "help", "menu", "gui", "start", "home", "sethome", "settings", "upgrade",
            "vault", "bank", "invite", "accept", "reject", "kick",
            "leave", "delete", "lobby", "info", "quest", "level",
            "story", "top", "theme", "role"
    );
    static final List<String> ADMIN_SUBCOMMANDS = List.of("setlobby", "reload", "admin");
    static final List<String> ALL_SUBCOMMANDS;

    static final List<String> BANK_ACTIONS = List.of("deposit", "withdraw");
    static final List<String> BANK_AMOUNTS = List.of("100", "1000", "5000", "10000");
    static final List<String> QUEST_ACTIONS = List.of("claim");
    static final List<String> LEVEL_ACTIONS = List.of("refresh");
    static final List<String> STORY_ACTIONS = List.of("start");
    static final List<String> ROLE_NAMES = List.of("member", "trusted", "co_owner");
    static final List<String> ADMIN_ACTIONS = List.of("delete", "reset", "tp", "story");
    static final List<String> ADMIN_STORY_ACTIONS = List.of("set", "get", "add");
    static final List<String> STORY_STAGE_SAMPLES = List.of("0", "1", "2", "3", "4", "5");

    static final List<String> HELP_LINES = List.of(
            "menu|gui \u00a77- Buka dashboard utama.",
            "start|home|sethome|lobby \u00a77- Navigasi island.",
            "info|level [refresh]|top \u00a77- Info dan leaderboard.",
            "settings|upgrade|vault|bank \u00a77- GUI, upgrade, storage, ekonomi.",
            "quest [claim <id>]|story [start <conversation>] \u00a77- Quest dan story.",
            "theme [id]|role <player> <role> \u00a77- Theme dan role member.",
            "invite|accept|reject|kick|leave|delete \u00a77- Manajemen island."
    );
    static final List<String> ADMIN_HELP_LINES = List.of(
            "setlobby|reload \u00a77- Admin maintenance.",
            "admin <delete|reset|tp> <player> \u00a77- Kelola island player.",
            "admin story <set|get|add> <player> [stage] \u00a77- Kelola progress story."
    );

    private static final Map<String, String> ALIASES = Map.of(
            "setting", "settings",
            "upgrades", "upgrade",
            "quests", "quest",
            "themes", "theme",
            "biome", "theme",
            "roles", "role"
    );
    private static final Set<String> ADMIN_ONLY_SUBCOMMANDS = Set.of("setlobby", "reload", "admin");

    static {
        List<String> all = new ArrayList<>(PLAYER_SUBCOMMANDS);
        all.addAll(ADMIN_SUBCOMMANDS);
        ALL_SUBCOMMANDS = List.copyOf(all);
    }

    private AcidIslandCommandSpec() {
    }

    static String normalize(String subcommand) {
        if (subcommand == null) {
            return "";
        }
        String lower = subcommand.toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(lower, lower);
    }

    static boolean isAdminOnly(String subcommand) {
        return ADMIN_ONLY_SUBCOMMANDS.contains(normalize(subcommand));
    }

    static String permissionNode(String subcommand) {
        return "acidisland.command." + normalize(subcommand);
    }
}
