package id.alvarennation.acidIsland.commands;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.hooks.VaultHook;
import id.alvarennation.acidIsland.island.Island;
import id.alvarennation.acidIsland.island.IslandManager;
import id.alvarennation.acidIsland.island.IslandRole;
import id.alvarennation.acidIsland.quest.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AcidIslandCommand implements CommandExecutor {

    private final AcidIsland plugin;

    public AcidIslandCommand(AcidIsland plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            handleReload(sender);
            return true;
        }
        if (sub.equals("admin")) {
            handleAdmin(sender, args, label);
            return true;
        }
        if (sub.equals("top")) {
            if (sender instanceof Player player && !hasCommandPermission(player, sub)) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
                return true;
            }
            handleTop(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPerintah ini hanya bisa dijalankan oleh player!");
            return true;
        }

        if (!hasCommandPermission(player, sub)) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return true;
        }

        switch (sub) {
            case "lobby" -> {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "teleport-lobby"));
                player.teleport(plugin.getLobbyLocation());
                player.setWorldBorder(null);
            }
            case "setlobby" -> handleSetLobby(player);
            case "start" -> {
                if (plugin.getIslandManager().hasIsland(player.getUniqueId())) {
                    teleportHome(player);
                } else if (!plugin.getIslandManager().canCreateIsland(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().format("&cKamu baru bisa membuat island lagi dalam " + formatDuration(plugin.getIslandManager().getCreateCooldownRemainingMillis(player.getUniqueId())) + "."));
                } else {
                    plugin.getIslandGUI().openStarterGUI(player);
                }
            }
            case "home" -> teleportHome(player);
            case "sethome" -> handleSetHome(player);
            case "setting", "settings" -> {
                Island island = requireIsland(player);
                if (island != null) {
                    plugin.getIslandGUI().openSettingsCategoryGUI(player, island);
                }
            }
            case "upgrade", "upgrades" -> {
                Island island = requireIsland(player);
                if (island != null) {
                    plugin.getIslandGUI().openUpgradesGUI(player, island);
                }
            }
            case "vault" -> {
                Island island = requireIsland(player);
                if (island != null) {
                    plugin.getIslandGUI().openVault(player, island);
                }
            }
            case "bank" -> handleBankCommand(player, args);
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /" + label + " invite <namaPlayer>"));
                    return true;
                }
                handleInvite(player, args[1]);
            }
            case "accept" -> handleAccept(player, args);
            case "reject" -> handleReject(player);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /" + label + " kick <namaPlayer>"));
                    return true;
                }
                handleKick(player, args[1]);
            }
            case "leave" -> handleLeave(player);
            case "delete" -> handleDelete(player);
            case "info" -> handleInfo(player);
            case "quest", "quests" -> handleQuestCommand(player, args);
            case "level" -> handleLevel(player, args);
            case "story" -> handleStoryCommand(player, args, label);
            case "role", "roles" -> handleRoleCommand(player, args, label);
            case "biome", "theme", "themes" -> handleThemeCommand(player, args);
            default -> sendHelp(player, label);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage("§cKamu tidak punya izin untuk melakukan ini!");
            return;
        }
        plugin.getIslandManager().saveData();
        plugin.getConfigManager().reload();
        plugin.getIslandManager().loadData();
        sender.sendMessage("§aAcidIsland config dan data berhasil direload.");
    }

    private void handleSetLobby(Player player) {
        if (!player.hasPermission("acidisland.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }
        plugin.setLobbyLocation(player.getLocation());
        player.sendMessage(plugin.getConfigManager().format("&aLobby AcidIsland berhasil diatur."));
    }

    private void handleAdmin(CommandSender sender, String[] args, String label) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage("§cKamu tidak punya izin untuk melakukan ini!");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("story")) {
            handleAdminStory(sender, args, label);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cPenggunaan: /" + label + " admin <delete|reset|tp> <player> atau /" + label + " admin story <set|get|add> <player> [stage]");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        UUID targetUuid = target.getUniqueId();

        switch (action) {
            case "delete" -> {
                Island deleted = plugin.getIslandManager().deleteIsland(targetUuid, true);
                if (deleted == null) {
                    sender.sendMessage("§cPlayer tersebut tidak punya island sebagai owner.");
                    return;
                }
                teleportParticipantsToLobby(deleted);
                sender.sendMessage("§aIsland milik " + args[2] + " dihapus dan cleanup dijadwalkan.");
            }
            case "reset" -> {
                Island oldIsland = plugin.getIslandManager().deleteIsland(targetUuid, true);
                if (oldIsland != null) {
                    teleportParticipantsToLobby(oldIsland);
                }
                Island newIsland = plugin.getIslandManager().createIsland(targetUuid, "classic");
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.teleport(newIsland.getHome(plugin.getWorldManager().getAcidWorld()));
                    plugin.getWorldManager().applyWorldBorder(onlineTarget, newIsland);
                    onlineTarget.sendMessage(plugin.getConfigManager().format("&aIsland kamu sudah direset oleh admin."));
                }
                sender.sendMessage("§aIsland milik " + args[2] + " sudah direset.");
            }
            case "tp" -> {
                if (!(sender instanceof Player adminPlayer)) {
                    sender.sendMessage("§cCommand tp hanya bisa dipakai player.");
                    return;
                }
                Island island = plugin.getIslandManager().getIslandByPlayer(targetUuid);
                if (island == null) {
                    sender.sendMessage("§cPlayer tersebut tidak punya island.");
                    return;
                }
                adminPlayer.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
                plugin.getWorldManager().applyWorldBorder(adminPlayer, island);
                adminPlayer.sendMessage(plugin.getConfigManager().format("&aTeleport ke island " + args[2] + "."));
            }
            default -> sender.sendMessage("§cPenggunaan: /" + label + " admin <delete|reset|tp> <player> atau /" + label + " admin story <set|get|add> <player> [stage]");
        }
    }

    private void handleAdminStory(CommandSender sender, String[] args, String label) {
        if (args.length < 4) {
            sender.sendMessage("§cPenggunaan: /" + label + " admin story <set|get|add> <player> [stage]");
            return;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        UUID targetUuid = target.getUniqueId();
        String targetName = target.getName() == null ? args[3] : target.getName();

        if (action.equals("get")) {
            sender.sendMessage("§aStory stage " + targetName + ": §e" + plugin.getIslandManager().getStoryStage(targetUuid));
            return;
        }

        if (args.length < 5) {
            sender.sendMessage("§cPenggunaan: /" + label + " admin story " + action + " <player> <stage>");
            return;
        }

        int value;
        try {
            value = Integer.parseInt(args[4]);
            if (value < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cStage harus angka 0 atau lebih besar.");
            return;
        }

        int newStage;
        if (action.equals("set")) {
            newStage = value;
        } else if (action.equals("add")) {
            newStage = plugin.getIslandManager().getStoryStage(targetUuid) + value;
        } else {
            sender.sendMessage("§cPenggunaan: /" + label + " admin story <set|get|add> <player> [stage]");
            return;
        }

        plugin.getIslandManager().setStoryStage(targetUuid, newStage);
        sender.sendMessage("§aStory stage " + targetName + " diset ke §e" + newStage + "§a.");
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.getConfigManager().format("&aStory progress kamu sekarang stage &e" + newStage + "&a."));
        }
    }

    private void teleportHome(Player player) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        player.sendMessage(plugin.getConfigManager().getMessage(player, "teleport-home"));
        player.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
        plugin.getWorldManager().applyWorldBorder(player, island);
    }

    private void handleSetHome(Player player) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }
        Island locIsland = plugin.getIslandManager().getIslandAt(player.getLocation());
        if (locIsland == null || !locIsland.getOwner().equals(island.getOwner())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "not-on-island"));
            return;
        }
        island.setHome(player.getLocation());
        plugin.getIslandManager().saveData();
        player.sendMessage(plugin.getConfigManager().getMessage(player, "home-set"));
    }

    private void handleBankCommand(Player player, String[] args) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();
        int bankLevel = island.getLevel("bank");
        double limit = config.getDouble("upgrades.bank." + bankLevel + ".limit", 10000.0);

        if (args.length == 1) {
            String limitStr = limit == -1 ? "Tanpa Batas" : "$" + limit;
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-info",
                    "{balance}", String.valueOf(island.getBankBalance()),
                    "{limit}", limitStr));
            player.sendMessage(plugin.getConfigManager().format("&7Gunakan &e/ai bank deposit <jumlah> &7atau &e/ai bank withdraw <jumlah>"));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /ai bank [deposit/withdraw] <jumlah>"));
            return;
        }

        String bankSub = args[1].toLowerCase(Locale.ROOT);
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (!Double.isFinite(amount) || amount <= 0) throw new NumberFormatException();
            amount = Math.round(amount * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().format("&cMasukkan jumlah uang yang valid!"));
            return;
        }

        if (!VaultHook.hasEconomy()) {
            player.sendMessage(plugin.getConfigManager().format("&cEconomy system belum tersambung!"));
            return;
        }

        if (bankSub.equals("deposit")) {
            double playerBal = VaultHook.getEconomy().getBalance(player);
            if (playerBal < amount || (limit != -1 && island.getBankBalance() + amount > limit)) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-fail"));
                return;
            }

            EconomyResponse response = VaultHook.getEconomy().withdrawPlayer(player, amount);
            if (!response.transactionSuccess()) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-fail"));
                return;
            }
            island.setBankBalance(island.getBankBalance() + amount);
            plugin.getIslandManager().saveData();
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-success", "{amount}", String.valueOf(amount)));
            return;
        }

        if (bankSub.equals("withdraw")) {
            if (!island.canUseBank(player.getUniqueId())) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
                return;
            }
            if (island.getBankBalance() < amount) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-fail"));
                return;
            }

            EconomyResponse response = VaultHook.getEconomy().depositPlayer(player, amount);
            if (!response.transactionSuccess()) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-fail"));
                return;
            }
            island.setBankBalance(island.getBankBalance() - amount);
            plugin.getIslandManager().saveData();
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-success", "{amount}", String.valueOf(amount)));
            return;
        }

        player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /ai bank [deposit/withdraw] <jumlah>"));
    }

    private void handleInvite(Player player, String targetName) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().format("&cPlayer tidak ditemukan!"));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().format("&cKamu tidak bisa mengundang dirimu sendiri!"));
            return;
        }

        if (island.isMember(target.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-already-member"));
            return;
        }

        int memberLevel = island.getLevel("members");
        int maxCapacity = plugin.getConfigManager().getConfig().getInt("upgrades.members." + memberLevel + ".capacity", 3);
        if (island.getMembers().size() >= maxCapacity) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-max-members"));
            return;
        }

        plugin.getIslandManager().addInvite(target.getUniqueId(), island.getOwner());
        player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-sent", "{player}", target.getName()));
        target.sendMessage(plugin.getConfigManager().getMessage(target, "invite-received", "{player}", player.getName()));
    }

    private void handleAccept(Player player, String[] args) {
        UUID ownerUuid = plugin.getIslandManager().getPendingInvite(player.getUniqueId());
        if (ownerUuid == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-no-pending"));
            return;
        }

        Island island = plugin.getIslandManager().getIslandByOwner(ownerUuid);
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().format("&cPulau pengundang sudah tidak ada!"));
            plugin.getIslandManager().removeInvite(player.getUniqueId());
            return;
        }

        int memberLevel = island.getLevel("members");
        int maxCapacity = plugin.getConfigManager().getConfig().getInt("upgrades.members." + memberLevel + ".capacity", 3);
        if (island.getMembers().size() >= maxCapacity) {
            player.sendMessage(plugin.getConfigManager().format("&cKapasitas pulau pengundang sudah penuh!"));
            plugin.getIslandManager().removeInvite(player.getUniqueId());
            return;
        }

        Island ownIsland = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (ownIsland != null) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                player.sendMessage(plugin.getConfigManager().format("&cKamu masih punya island sendiri. Kalau lanjut, island kamu akan dihapus permanen."));
                player.sendMessage(plugin.getConfigManager().format("&eGunakan &b/ai accept confirm &euntuk konfirmasi bergabung."));
                return;
            }
            Island deleted = plugin.getIslandManager().deleteIsland(player.getUniqueId());
            if (deleted != null) {
                teleportParticipantsToLobby(deleted);
            }
        } else {
            plugin.getIslandManager().removePlayerFromCurrentIsland(player.getUniqueId());
        }

        plugin.getIslandManager().addMember(island, player.getUniqueId());
        plugin.getIslandManager().removeInvite(player.getUniqueId());

        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
        String ownerName = owner.getName() == null ? ownerUuid.toString() : owner.getName();
        player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-accepted", "{owner}", ownerName));

        Player onlineOwner = Bukkit.getPlayer(ownerUuid);
        if (onlineOwner != null) {
            onlineOwner.sendMessage(plugin.getConfigManager().getMessage(onlineOwner, "invite-accepted-owner", "{player}", player.getName()));
        }

        player.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
        plugin.getWorldManager().applyWorldBorder(player, island);
    }

    private void handleReject(Player player) {
        UUID ownerUuid = plugin.getIslandManager().getPendingInvite(player.getUniqueId());
        if (ownerUuid == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-no-pending"));
            return;
        }

        plugin.getIslandManager().removeInvite(player.getUniqueId());
        player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-rejected"));

        Player onlineOwner = Bukkit.getPlayer(ownerUuid);
        if (onlineOwner != null) {
            onlineOwner.sendMessage(plugin.getConfigManager().getMessage(onlineOwner, "invite-rejected-owner", "{player}", player.getName()));
        }
    }

    private void handleKick(Player player, String targetName) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();
        if (!island.getMembers().contains(targetUuid)) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "kick-failed", "{player}", targetName));
            return;
        }

        IslandRole actorRole = island.getRole(player.getUniqueId());
        IslandRole targetRole = island.getRole(targetUuid);
        if (!actorRole.atLeast(IslandRole.OWNER) && targetRole.atLeast(actorRole)) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }

        plugin.getIslandManager().removeMember(island, targetUuid);
        player.sendMessage(plugin.getConfigManager().getMessage(player, "kick-success", "{player}", targetName));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.getConfigManager().getMessage(onlineTarget, "kick-notification"));
            onlineTarget.setWorldBorder(null);
            onlineTarget.teleport(plugin.getLobbyLocation());
        }
    }

    private void handleLeave(Player player) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }

        if (island.isOwner(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "leave-owner"));
            return;
        }

        plugin.getIslandManager().removeMember(island, player.getUniqueId());
        player.sendMessage(plugin.getConfigManager().getMessage(player, "leave-success"));

        player.setWorldBorder(null);
        player.teleport(plugin.getLobbyLocation());
    }

    private void handleDelete(Player player) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
            return;
        }
        if (!plugin.getIslandManager().canDeleteIsland(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().format("&cKamu baru bisa menghapus island lagi dalam " + formatDuration(plugin.getIslandManager().getDeleteCooldownRemainingMillis(player.getUniqueId())) + "."));
            return;
        }
        plugin.getIslandGUI().openDeleteConfirmGUI(player, island);
    }

    private void handleInfo(Player player) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
        String ownerName = owner.getName() == null ? island.getOwner().toString() : owner.getName();
        long value = plugin.getIslandManager().getIslandValue(island, false);
        int islandLevel = plugin.getIslandManager().getIslandLevel(island, false);

        player.sendMessage(plugin.getConfigManager().format("&b=== &3Info Pulau &b==="));
        player.sendMessage(plugin.getConfigManager().format("&7Owner: &e" + ownerName));
        player.sendMessage(plugin.getConfigManager().format("&7Theme: &e" + island.getTheme()));
        player.sendMessage(plugin.getConfigManager().format("&7Island Level: &e" + islandLevel + " &7(Value: &e" + value + "&7)"));

        List<String> memberNames = new ArrayList<>();
        for (UUID uuid : island.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            memberNames.add((member.getName() == null ? uuid.toString() : member.getName()) + " (" + island.getRole(uuid).getDisplayName() + ")");
        }
        player.sendMessage(plugin.getConfigManager().format("&7Anggota: &e" + (memberNames.isEmpty() ? "-" : String.join(", ", memberNames))));
        player.sendMessage(plugin.getConfigManager().format("&7Lokasi Center: &e" + island.getX() + ", Y=75, " + island.getZ()));
        player.sendMessage(plugin.getConfigManager().format("&7Tingkat Upgrade:"));
        player.sendMessage(plugin.getConfigManager().format("  &7- World Border: &eTier " + island.getLevel("border")));
        player.sendMessage(plugin.getConfigManager().format("  &7- Kapasitas Anggota: &eTier " + island.getLevel("members")));
        player.sendMessage(plugin.getConfigManager().format("  &7- Vault: &eTier " + island.getLevel("vault")));
        player.sendMessage(plugin.getConfigManager().format("  &7- Minion Limit: &eTier " + island.getLevel("minions")));
        player.sendMessage(plugin.getConfigManager().format("  &7- Bank Limit: &eTier " + island.getLevel("bank")));
        player.sendMessage(plugin.getConfigManager().format("  &7- Generator Ore: &eTier " + island.getLevel("generator")));
        player.sendMessage(plugin.getConfigManager().format("  &7- Saldo Bank: &a$" + island.getBankBalance()));
    }

    private void handleQuestCommand(Player player, String[] args) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }

        if (args.length == 1) {
            plugin.getIslandGUI().openQuestsGUI(player, island);
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("claim")) {
            claimQuest(player, island, args[2]);
            return;
        }

        player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /ai quest atau /ai quest claim <id>"));
    }

    public void claimQuest(Player player, Island island, String questId) {
        QuestManager.ClaimResult result = plugin.getQuestManager().claim(player, island, questId);
        switch (result) {
            case CLAIMED -> player.sendMessage(plugin.getConfigManager().format("&aQuest &e" + plugin.getQuestManager().getDisplayName(questId) + " &aberhasil diklaim."));
            case ALREADY_COMPLETED -> player.sendMessage(plugin.getConfigManager().format("&cQuest ini sudah selesai."));
            case REQUIREMENTS_NOT_MET -> player.sendMessage(plugin.getConfigManager().format("&cRequirement quest belum terpenuhi."));
            case NOT_FOUND -> player.sendMessage(plugin.getConfigManager().format("&cQuest tidak ditemukan."));
            case NO_ISLAND -> player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
            case REWARD_FAILED -> player.sendMessage(plugin.getConfigManager().format("&cReward quest gagal diproses. Coba lagi nanti."));
        }
    }

    private void handleLevel(Player player, String[] args) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        boolean refresh = args.length > 1 && args[1].equalsIgnoreCase("refresh") && island.canManage(player.getUniqueId());
        long value = plugin.getIslandManager().getIslandValue(island, refresh);
        int level = plugin.getIslandManager().getIslandLevel(island, false);
        player.sendMessage(plugin.getConfigManager().format("&7Island Level: &e" + level + " &7(Value: &e" + value + "&7)"));
        if (island.isLevelScanInProgress()) {
            player.sendMessage(plugin.getConfigManager().format("&7Value sedang dihitung ulang bertahap. Angka di atas memakai cache terakhir."));
        }
    }

    private void handleStoryCommand(Player player, String[] args, String label) {
        int playerStage = plugin.getIslandManager().getStoryStage(player.getUniqueId());
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        int islandStage = island == null ? playerStage : plugin.getIslandManager().getIslandStoryStage(island);

        if (args.length == 1) {
            player.sendMessage(plugin.getConfigManager().format("&b=== &3Story Progress &b==="));
            player.sendMessage(plugin.getConfigManager().format("&7Stage kamu: &e" + playerStage));
            player.sendMessage(plugin.getConfigManager().format("&7Stage island/team: &e" + islandStage));
            player.sendMessage(plugin.getConfigManager().format("&7Gunakan &e/" + label + " story start <conversation> &7untuk mulai dialog ConverseCraft."));
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("start")) {
            if (plugin.getConverseCraftHook() == null || !plugin.getConverseCraftHook().isEnabled()) {
                player.sendMessage(plugin.getConfigManager().format("&cConverseCraft belum aktif di server ini."));
                return;
            }
            boolean started = plugin.getConverseCraftHook().startConversation(player, args[2]);
            if (!started) {
                player.sendMessage(plugin.getConfigManager().format("&cConversation &e" + args[2] + " &cgagal dimulai atau tidak ditemukan."));
            }
            return;
        }

        player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /" + label + " story atau /" + label + " story start <conversation>"));
    }

    private void handleTop(CommandSender sender) {
        List<IslandManager.IslandRanking> top = plugin.getIslandManager().getTopIslands(10);
        sender.sendMessage("§b=== §3Top AcidIsland §b===");
        int rank = 1;
        for (IslandManager.IslandRanking entry : top) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.island().getOwner());
            String name = owner.getName() == null ? entry.island().getOwner().toString() : owner.getName();
            sender.sendMessage("§e#" + rank + " §f" + name + " §7- Level §a" + entry.level() + " §7(Value §a" + entry.value() + "§7)");
            rank++;
        }
        if (top.isEmpty()) {
            sender.sendMessage("§7Belum ada island.");
        }
    }

    private void handleRoleCommand(Player player, String[] args, String label) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        if (!island.canChangeRoles(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /" + label + " role <player> <member|trusted|co_owner>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetUuid = target.getUniqueId();
        if (!island.getMembers().contains(targetUuid)) {
            player.sendMessage(plugin.getConfigManager().format("&cPlayer tersebut bukan member island kamu."));
            return;
        }

        IslandRole role = IslandRole.fromString(args[2]);
        if (role == IslandRole.OWNER || role == IslandRole.VISITOR) {
            player.sendMessage(plugin.getConfigManager().format("&cRole tidak valid. Gunakan member, trusted, atau co_owner."));
            return;
        }

        plugin.getIslandManager().setMemberRole(island, targetUuid, role);
        player.sendMessage(plugin.getConfigManager().format("&aRole " + args[1] + " diubah ke &e" + role.getDisplayName() + "&a."));
    }

    private void handleThemeCommand(Player player, String[] args) {
        Island island = requireIsland(player);
        if (island == null) {
            return;
        }
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }
        if (args.length == 1) {
            plugin.getIslandGUI().openThemesGUI(player, island);
            return;
        }
        changeTheme(player, island, args[1]);
    }

    public void changeTheme(Player player, Island island, String themeId) {
        String normalized = themeId.toLowerCase(Locale.ROOT);
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "themes." + normalized;
        if (config.getConfigurationSection(path) == null) {
            player.sendMessage(plugin.getConfigManager().format("&cTheme tidak ditemukan."));
            return;
        }
        if (!plugin.getWorldManager().isThemeValid(normalized)) {
            player.sendMessage(plugin.getConfigManager().format("&cTheme gagal diterapkan. Cek nama biome di config."));
            return;
        }
        if (island.getTheme().equalsIgnoreCase(normalized)) {
            player.sendMessage(plugin.getConfigManager().format("&cIsland kamu sudah memakai theme ini."));
            return;
        }

        double cost = config.getDouble(path + ".cost", 0.0);
        if (cost > 0) {
            if (!VaultHook.hasEconomy()) {
                player.sendMessage(plugin.getConfigManager().format("&cEconomy system belum tersambung!"));
                return;
            }
            if (VaultHook.getEconomy().getBalance(player) < cost) {
                player.sendMessage(plugin.getConfigManager().format("&cUang kamu tidak cukup. Butuh $" + cost + "."));
                return;
            }
            EconomyResponse response = VaultHook.getEconomy().withdrawPlayer(player, cost);
            if (!response.transactionSuccess()) {
                player.sendMessage(plugin.getConfigManager().format("&cPembayaran theme gagal: " + response.errorMessage));
                return;
            }
        }

        if (plugin.getWorldManager().applyIslandTheme(island, normalized)) {
            player.sendMessage(plugin.getConfigManager().format("&aTheme island diubah ke &e" + config.getString(path + ".display-name", normalized) + "&a."));
        } else {
            player.sendMessage(plugin.getConfigManager().format("&cTheme gagal diterapkan. Cek nama biome di config."));
        }
    }

    private Island requireIsland(Player player) {
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
        }
        return island;
    }

    private void teleportParticipantsToLobby(Island island) {
        Location lobby = plugin.getLobbyLocation();
        for (UUID uuid : island.getParticipants()) {
            Player participant = Bukkit.getPlayer(uuid);
            if (participant != null) {
                participant.setWorldBorder(null);
                participant.teleport(lobby);
            }
        }
    }

    private boolean hasCommandPermission(Player player, String sub) {
        if (player.hasPermission("acidisland.admin")) {
            return true;
        }
        String normalized = switch (sub) {
            case "setting" -> "settings";
            case "upgrades" -> "upgrade";
            case "quests" -> "quest";
            case "themes", "biome" -> "theme";
            case "roles" -> "role";
            default -> sub;
        };
        return player.hasPermission("acidisland.command." + normalized);
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("acidisland.admin");
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return remainingSeconds + " detik";
        }
        return minutes + " menit " + remainingSeconds + " detik";
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§b=== §9§lAcidIsland Help §b===");
        sender.sendMessage("§e/" + label + " start §7- Membuat pulau baru atau teleport ke rumah.");
        sender.sendMessage("§e/" + label + " home §7- Teleportasi ke rumah pulau.");
        sender.sendMessage("§e/" + label + " sethome §7- Mengatur lokasi home pulau.");
        sender.sendMessage("§e/" + label + " info §7- Menampilkan informasi detail pulau.");
        sender.sendMessage("§e/" + label + " setting §7- Mengubah pengaturan pulau.");
        sender.sendMessage("§e/" + label + " upgrade §7- Upgrade kemampuan pulau.");
        sender.sendMessage("§e/" + label + " vault §7- Membuka virtual chest.");
        sender.sendMessage("§e/" + label + " bank §7- Deposit/withdraw bank pulau.");
        sender.sendMessage("§e/" + label + " quest §7- Membuka quest pulau.");
        sender.sendMessage("§e/" + label + " level [refresh] §7- Cek level/value island.");
        sender.sendMessage("§e/" + label + " story [start <conversation>] §7- Cek/mulai story ConverseCraft.");
        sender.sendMessage("§e/" + label + " top §7- Leaderboard island.");
        sender.sendMessage("§e/" + label + " theme §7- Ganti biome/theme island.");
        sender.sendMessage("§e/" + label + " role <player> <role> §7- Atur role member.");
        sender.sendMessage("§e/" + label + " invite/kick/leave/delete/lobby §7- Manajemen island.");
        if (hasAdminPermission(sender)) {
            sender.sendMessage("§c/" + label + " setlobby, reload, admin <delete|reset|tp> <player>");
            sender.sendMessage("§c/" + label + " admin story <set|get|add> <player> [stage]");
        }
    }
}
