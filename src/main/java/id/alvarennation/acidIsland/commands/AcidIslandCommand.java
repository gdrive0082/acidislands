package id.alvarennation.acidIsland.commands;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.gui.IslandGUI;
import id.alvarennation.acidIsland.hooks.VaultHook;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AcidIslandCommand implements CommandExecutor {

    private final AcidIsland plugin;

    public AcidIslandCommand(AcidIsland plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPerintah ini hanya bisa dijalankan oleh player!");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "lobby" -> {
                Location spawnLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
                player.sendMessage(plugin.getConfigManager().getMessage(player, "teleport-lobby"));
                player.teleport(spawnLoc);
                player.setWorldBorder(null);
            }
            case "start" -> {
                if (plugin.getIslandManager().hasIsland(player.getUniqueId())) {
                    teleportHome(player);
                } else {
                    plugin.getIslandGUI().openStarterGUI(player);
                }
            }
            case "home" -> teleportHome(player);
            case "sethome" -> {
                Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
                if (island == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
                    return true;
                }
                // Check if player is on their own island
                Island locIsland = plugin.getIslandManager().getIslandAt(player.getLocation());
                if (locIsland == null || !locIsland.getOwner().equals(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "not-on-island"));
                    return true;
                }
                island.setHome(player.getLocation());
                plugin.getIslandManager().saveData();
                player.sendMessage(plugin.getConfigManager().getMessage(player, "home-set"));
            }
            case "setting", "settings" -> {
                Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
                if (island == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
                    return true;
                }
                plugin.getIslandGUI().openSettingsCategoryGUI(player, island);
            }
            case "upgrade", "upgrades" -> {
                Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
                if (island == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
                    return true;
                }
                plugin.getIslandGUI().openUpgradesGUI(player, island);
            }
            case "vault" -> {
                Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
                if (island == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
                    return true;
                }
                openVault(player, island);
            }
            case "bank" -> handleBankCommand(player, args);
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /" + label + " invite <namaPlayer>"));
                    return true;
                }
                handleInvite(player, args[1]);
            }
            case "accept" -> handleAccept(player);
            case "reject" -> handleReject(player);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /" + label + " kick <namaPlayer>"));
                    return true;
                }
                handleKick(player, args[1]);
            }
            case "leave" -> handleLeave(player);
            case "delete" -> {
                Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
                if (island == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
                    return true;
                }
                plugin.getIslandGUI().openDeleteConfirmGUI(player, island);
            }
            case "info" -> handleInfo(player);
            default -> sendHelp(player, label);
        }

        return true;
    }

    private void teleportHome(Player player) {
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
            return;
        }
        player.sendMessage(plugin.getConfigManager().getMessage(player, "teleport-home"));
        player.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
        plugin.getWorldManager().applyWorldBorder(player, island);
    }

    private void openVault(Player player, Island island) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int level = island.getLevel("vault");
        int rows = config.getInt("upgrades.vault." + level + ".rows", 1);

        Inventory inv = Bukkit.createInventory(new IslandGUI.AcidIslandHolder("vault", island), rows * 9, plugin.getConfigManager().format("&6&lIsland Vault"));

        String base64 = island.getVaultBase64();
        if (base64 != null && !base64.isEmpty()) {
            ItemStack[] items = Island.itemStackArrayFromBase64(base64);
            // safe population
            for (int i = 0; i < Math.min(items.length, inv.getSize()); i++) {
                inv.setItem(i, items[i]);
            }
        }

        player.openInventory(inv);
    }

    private void handleBankCommand(Player player, String[] args) {
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
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

        String sub = args[1].toLowerCase();
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().format("&cMasukkan jumlah uang yang valid!"));
            return;
        }

        if (!VaultHook.hasEconomy()) {
            player.sendMessage(plugin.getConfigManager().format("&cEcon system not connected!"));
            return;
        }

        if (sub.equals("deposit")) {
            double playerBal = VaultHook.getEconomy().getBalance(player);
            if (playerBal < amount) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-fail"));
                return;
            }

            if (limit != -1 && island.getBankBalance() + amount > limit) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-fail"));
                player.sendMessage(plugin.getConfigManager().format("&cKapasitas bank tidak mencukupi!"));
                return;
            }

            VaultHook.getEconomy().withdrawPlayer(player, amount);
            island.setBankBalance(island.getBankBalance() + amount);
            plugin.getIslandManager().saveData();
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-success", "{amount}", String.valueOf(amount)));
        } else if (sub.equals("withdraw")) {
            if (island.getBankBalance() < amount) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-fail"));
                return;
            }

            island.setBankBalance(island.getBankBalance() - amount);
            VaultHook.getEconomy().depositPlayer(player, amount);
            plugin.getIslandManager().saveData();
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-success", "{amount}", String.valueOf(amount)));
        } else {
            player.sendMessage(plugin.getConfigManager().format("&cPenggunaan: /ai bank [deposit/withdraw] <jumlah>"));
        }
    }

    private void handleInvite(Player player, String targetName) {
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
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

        if (island.getMembers().contains(target.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-already-member"));
            return;
        }

        // Cek kapasitas member island
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int memberLevel = island.getLevel("members");
        int maxCapacity = config.getInt("upgrades.members." + memberLevel + ".capacity", 3);

        if (island.getMembers().size() >= maxCapacity) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-max-members"));
            return;
        }

        plugin.getIslandManager().addInvite(target.getUniqueId(), player.getUniqueId());
        player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-sent", "{player}", target.getName()));
        target.sendMessage(plugin.getConfigManager().getMessage(target, "invite-received", "{player}", player.getName()));
    }

    private void handleAccept(Player player) {
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

        // Cek kapasitas
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int memberLevel = island.getLevel("members");
        int maxCapacity = config.getInt("upgrades.members." + memberLevel + ".capacity", 3);

        if (island.getMembers().size() >= maxCapacity) {
            player.sendMessage(plugin.getConfigManager().format("&cKapasitas pulau pengundang sudah penuh!"));
            plugin.getIslandManager().removeInvite(player.getUniqueId());
            return;
        }

        // Hapus pulau sendiri jika punya
        if (plugin.getIslandManager().hasIsland(player.getUniqueId())) {
            Island pIsland = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
            if (pIsland != null) {
                plugin.getIslandManager().deleteIsland(player.getUniqueId());
            }
        }

        plugin.getIslandManager().addMember(island, player.getUniqueId());
        plugin.getIslandManager().removeInvite(player.getUniqueId());

        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
        player.sendMessage(plugin.getConfigManager().getMessage(player, "invite-accepted", "{owner}", owner.getName()));
        
        Player onlineOwner = Bukkit.getPlayer(ownerUuid);
        if (onlineOwner != null) {
            onlineOwner.sendMessage(plugin.getConfigManager().getMessage(onlineOwner, "invite-accepted-owner", "{player}", player.getName()));
        }

        // Teleport to new island home
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
        Island island = plugin.getIslandManager().getIslandByOwner(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();

        if (!island.getMembers().contains(targetUuid)) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "kick-failed", "{player}", targetName));
            return;
        }

        plugin.getIslandManager().removeMember(island, targetUuid);
        player.sendMessage(plugin.getConfigManager().getMessage(player, "kick-success", "{player}", targetName));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.getConfigManager().getMessage(onlineTarget, "kick-notification"));
            // Teleport to lobby and reset border
            onlineTarget.setWorldBorder(null);
            Location lobbyLoc = plugin.getLobbyLocation();
            if (lobbyLoc != null) {
                onlineTarget.teleport(lobbyLoc);
            } else {
                onlineTarget.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
    }

    private void handleLeave(Player player) {
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
            return;
        }

        if (island.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "leave-owner"));
            return;
        }

        plugin.getIslandManager().removeMember(island, player.getUniqueId());
        player.sendMessage(plugin.getConfigManager().getMessage(player, "leave-success"));

        // Reset border and teleport to lobby
        player.setWorldBorder(null);
        Location lobbyLoc = plugin.getLobbyLocation();
        if (lobbyLoc != null) {
            player.teleport(lobbyLoc);
        } else {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    private void handleInfo(Player player) {
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island == null) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
            return;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
        
        player.sendMessage(plugin.getConfigManager().format("&b=== &3Info Pulau &b==="));
        player.sendMessage(plugin.getConfigManager().format("&7Owner: &e" + owner.getName()));
        
        List<String> memberNames = new ArrayList<>();
        for (UUID uuid : island.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            memberNames.add(member.getName());
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

    private void sendHelp(Player player, String label) {
        player.sendMessage(plugin.getConfigManager().format("&b=== &9&lAcidIsland Help &b==="));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " help &7- Menampilkan bantuan."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " start &7- Membuat pulau baru atau teleport ke rumah."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " home &7- Teleportasi ke rumah pulau Anda."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " sethome &7- Mengatur lokasi home pulau."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " info &7- Menampilkan informasi detail pulau."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " setting &7- Mengubah pengaturan dasar/premium pulau."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " upgrade &7- Mengupgrade kemampuan pulau Anda."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " vault &7- Membuka virtual chest penyimpanan blok."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " bank &7- Mengatur keuangan pulau (deposit/withdraw)."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " invite <player> &7- Mengundang player ke pulau Anda."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " accept &7- Menerima undangan dari pulau lain."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " reject &7- Menolak undangan dari pulau lain."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " kick <player> &7- Mengeluarkan anggota dari pulau."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " leave &7- Meninggalkan pulau yang Anda gabungi."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " delete &7- Menghapus pulau Anda secara permanen."));
        player.sendMessage(plugin.getConfigManager().format("&e/" + label + " lobby &7- Teleportasi ke lobby utama."));
    }
}
