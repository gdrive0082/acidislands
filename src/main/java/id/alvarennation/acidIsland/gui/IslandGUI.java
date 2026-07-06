package id.alvarennation.acidIsland.gui;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.hooks.VaultHook;
import id.alvarennation.acidIsland.island.Island;
import id.alvarennation.acidIsland.island.IslandRole;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class IslandGUI implements Listener {

    private final AcidIsland plugin;
    private final Map<UUID, Inventory> openVaults = new HashMap<>();

    public IslandGUI(AcidIsland plugin) {
        this.plugin = plugin;
    }

    public static class AcidIslandHolder implements InventoryHolder {
        private final String guiType;
        private final Island island;
        private final String context;
        private final int page;

        public AcidIslandHolder(String guiType, Island island) {
            this(guiType, island, "", 0);
        }

        public AcidIslandHolder(String guiType, Island island, String context, int page) {
            this.guiType = guiType;
            this.island = island;
            this.context = context == null ? "" : context;
            this.page = Math.max(0, page);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(this, 27);
        }

        public String getGuiType() {
            return guiType;
        }

        public Island getIsland() {
            return island;
        }

        public String getContext() {
            return context;
        }

        public int getPage() {
            return page;
        }
    }

    private ItemStack createGuiItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(plugin.getConfigManager().format(name));
            List<Component> compLore = new ArrayList<>();
            for (String line : lore) {
                compLore.add(plugin.getConfigManager().format(line));
            }
            meta.lore(compLore);
        });
        return item;
    }

    private void fillFiller(Inventory inv) {
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    // ==========================================
    // 0. Main Dashboard GUI
    // ==========================================
    public void openMainMenu(Player player) {
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island == null) {
            Inventory inv = Bukkit.createInventory(new AcidIslandHolder("main", null), 27, plugin.getConfigManager().format("&3&lAcidIsland Menu"));
            inv.setItem(4, createGuiItem(
                    Material.OAK_SAPLING,
                    "&a&lMulai AcidIsland",
                    "&7Kamu belum punya island.",
                    "&7Pilih starter island untuk mulai."
            ));
            inv.setItem(11, createGuiItem(Material.GRASS_BLOCK, "&aBuat Island", "&7Buka pilihan starter island.", "&eKlik untuk mulai."));
            inv.setItem(13, createGuiItem(Material.NETHER_STAR, "&bTop Island", "&7Lihat ranking island terbaik.", "&eKlik untuk membuka leaderboard."));
            inv.setItem(15, createGuiItem(Material.ENDER_PEARL, "&eKe Lobby", "&7Teleport ke lobby AcidIsland.", "&eKlik untuk teleport."));
            if (player.hasPermission("acidisland.admin")) {
                inv.setItem(22, createGuiItem(Material.COMMAND_BLOCK, "&cAdmin Panel", "&7Buka dashboard admin.", "&eKlik untuk membuka."));
            }
            fillFiller(inv);
            player.openInventory(inv);
            return;
        }

        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("main", island), 45, plugin.getConfigManager().format("&3&lAcidIsland Dashboard"));
        OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
        int islandLevel = plugin.getIslandManager().getIslandLevel(island, false);
        long value = plugin.getIslandManager().getIslandValue(island, false, false);
        int storyStage = plugin.getIslandManager().getIslandStoryStage(island);
        int borderSize = plugin.getIslandManager().getBorderSize(island);
        IslandRole role = island.getRole(player.getUniqueId());

        inv.setItem(4, createGuiItem(
                Material.FILLED_MAP,
                "&b&lRingkasan Island",
                "&7Owner: &e" + getOfflineName(owner),
                "&7Role kamu: &e" + role.getDisplayName(),
                "&7Theme: &e" + island.getTheme(),
                "&7Level: &a" + islandLevel + " &7(Value &a" + value + "&7)",
                "&7Story Stage: &d" + storyStage,
                "&7Border: &b" + borderSize + "x" + borderSize,
                "&7Bank: &6" + formatMoney(island.getBankBalance()),
                island.isLevelScanInProgress() ? "&eValue sedang dihitung ulang." : "&7Value memakai cache terbaru."
        ));

        inv.setItem(10, createGuiItem(Material.ENDER_PEARL, "&aHome", "&7Teleport ke home island.", "&eKlik untuk teleport."));
        inv.setItem(11, createGuiItem(Material.COMPASS, "&eSet Home", "&7Atur home island di lokasi kamu.", island.canManage(player.getUniqueId()) ? "&eKlik untuk set home." : "&cButuh role Co-Owner."));
        inv.setItem(12, createGuiItem(Material.REPEATER, "&9Settings", "&7Atur proteksi, visitor, dan premium setting.", "&eKlik untuk membuka."));
        inv.setItem(13, createGuiItem(Material.ANVIL, "&6Upgrades", "&7Upgrade border, member, vault, bank, minion, generator.", "&eKlik untuk membuka."));
        inv.setItem(14, createGuiItem(Material.CHEST, "&6Vault", "&7Storage bersama island.", "&eKlik untuk membuka."));
        inv.setItem(15, createGuiItem(Material.GOLD_INGOT, "&eBank", "&7Deposit/withdraw saldo island via GUI.", "&eKlik untuk membuka."));
        inv.setItem(16, createGuiItem(Material.WRITABLE_BOOK, "&aQuests", "&7Lihat dan claim quest island.", "&eKlik untuk membuka."));

        inv.setItem(20, createGuiItem(Material.GRASS_BLOCK, "&bThemes", "&7Ubah biome/theme island.", island.canManage(player.getUniqueId()) ? "&eKlik untuk membuka." : "&cButuh role Co-Owner."));
        inv.setItem(21, createGuiItem(Material.AMETHYST_SHARD, "&dStory", "&7Lihat stage dan mulai chapter ConverseCraft.", "&eKlik untuk membuka."));
        inv.setItem(22, createGuiItem(Material.NETHER_STAR, "&bTop Island", "&7Lihat ranking island terbaik.", "&eKlik untuk membuka."));
        inv.setItem(23, createGuiItem(Material.BELL, "&eLobby", "&7Teleport ke lobby AcidIsland.", "&eKlik untuk teleport."));
        inv.setItem(24, createGuiItem(
                island.isOwner(player.getUniqueId()) ? Material.TNT : Material.BARRIER,
                "&cHapus Island",
                island.isOwner(player.getUniqueId()) ? "&7Buka konfirmasi hapus island." : "&cHanya owner bisa menghapus island.",
                island.isOwner(player.getUniqueId()) ? "&eKlik untuk konfirmasi." : "&7Gunakan /ai leave untuk keluar."
        ));

        inv.setItem(31, createGuiItem(Material.EXPERIENCE_BOTTLE, "&aRefresh Level", "&7Jadwalkan hitung ulang value island.", island.canManage(player.getUniqueId()) ? "&eKlik untuk scan ulang." : "&cButuh role Co-Owner."));
        if (player.hasPermission("acidisland.admin")) {
            inv.setItem(40, createGuiItem(Material.COMMAND_BLOCK, "&cAdmin Panel", "&7Buka dashboard admin.", "&eKlik untuk membuka."));
        }

        fillFiller(inv);
        player.openInventory(inv);
    }

    public void openBankGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("bank", island), 36, plugin.getConfigManager().format("&6&lIsland Bank"));
        double limit = getBankLimit(island);
        String limitText = limit < 0 ? "Tanpa Batas" : formatMoney(limit);
        String wallet = VaultHook.hasEconomy() ? formatMoney(VaultHook.getEconomy().getBalance(player)) : "Economy offline";

        inv.setItem(4, createGuiItem(
                Material.GOLD_BLOCK,
                "&e&lSaldo Bank",
                "&7Bank: &6" + formatMoney(island.getBankBalance()),
                "&7Limit: &e" + limitText,
                "&7Wallet kamu: &a" + wallet,
                "&7Withdraw butuh role Trusted ke atas."
        ));

        inv.setItem(10, createGuiItem(Material.LIME_DYE, "&aDeposit $100", "&7Masukkan $100 ke bank island."));
        inv.setItem(11, createGuiItem(Material.LIME_DYE, "&aDeposit $1,000", "&7Masukkan $1,000 ke bank island."));
        inv.setItem(12, createGuiItem(Material.LIME_DYE, "&aDeposit $10,000", "&7Masukkan $10,000 ke bank island."));
        inv.setItem(13, createGuiItem(Material.EMERALD, "&aDeposit Maksimal", "&7Masukkan saldo sebanyak yang muat."));

        inv.setItem(19, createGuiItem(Material.RED_DYE, "&cWithdraw $100", "&7Ambil $100 dari bank island."));
        inv.setItem(20, createGuiItem(Material.RED_DYE, "&cWithdraw $1,000", "&7Ambil $1,000 dari bank island."));
        inv.setItem(21, createGuiItem(Material.RED_DYE, "&cWithdraw $10,000", "&7Ambil $10,000 dari bank island."));
        inv.setItem(22, createGuiItem(Material.GOLD_NUGGET, "&cWithdraw Semua", "&7Ambil semua saldo bank island."));
        inv.setItem(31, createGuiItem(Material.ARROW, "&eKembali", "&7Kembali ke dashboard."));

        if (!VaultHook.hasEconomy()) {
            inv.setItem(16, createGuiItem(Material.BARRIER, "&cEconomy Belum Tersambung", "&7Deposit dan withdraw tidak tersedia."));
        }

        fillFiller(inv);
        player.openInventory(inv);
    }

    public void openStoryGUI(Player player) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("story", null), 36, plugin.getConfigManager().format("&d&lStory Progress"));
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        int playerStage = plugin.getIslandManager().getStoryStage(player.getUniqueId());
        int islandStage = island == null ? playerStage : plugin.getIslandManager().getIslandStoryStage(island);
        boolean hookEnabled = plugin.getConverseCraftHook() != null && plugin.getConverseCraftHook().isEnabled();

        inv.setItem(4, createGuiItem(
                hookEnabled ? Material.AMETHYST_CLUSTER : Material.BARRIER,
                "&d&lStory AcidIsland",
                "&7Stage kamu: &e" + playerStage,
                "&7Stage island/team: &e" + islandStage,
                hookEnabled ? "&aConverseCraft aktif." : "&cConverseCraft belum aktif.",
                "&7Chapter diambil dari config integrations.conversecraft."
        ));

        List<String> conversations = getStoryConversationIds();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        if (conversations.isEmpty()) {
            inv.setItem(13, createGuiItem(Material.BARRIER, "&cBelum Ada Chapter", "&7Isi integrations.conversecraft.story-stage-conversations di config."));
        } else {
            boolean sequential = plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.enforce-sequential", true);
            for (int i = 0; i < Math.min(conversations.size(), slots.length); i++) {
                String conversation = conversations.get(i);
                int mappedStage = plugin.getConverseCraftHook() == null ? 0 : plugin.getConverseCraftHook().getMappedStoryStage(conversation);
                boolean completed = mappedStage > 0 && playerStage >= mappedStage;
                boolean locked = hookEnabled && mappedStage > 0 && sequential && mappedStage > playerStage + 1;
                Material icon = !hookEnabled ? Material.BARRIER : completed ? Material.LIME_DYE : locked ? Material.REDSTONE : Material.ENCHANTED_BOOK;
                inv.setItem(slots[i], createGuiItem(
                        icon,
                        "&d" + conversation,
                        mappedStage > 0 ? "&7Target stage: &e" + mappedStage : "&7Target stage: &eManual",
                        completed ? "&aSudah selesai/terbuka." : locked ? "&cChapter sebelumnya belum selesai." : "&eKlik untuk mulai conversation.",
                        hookEnabled ? "&7Integrasi ConverseCraft siap." : "&cConverseCraft belum aktif."
                ));
            }
        }

        inv.setItem(31, createGuiItem(Material.ARROW, "&eKembali", "&7Kembali ke dashboard."));
        fillFiller(inv);
        player.openInventory(inv);
    }

    public void openTopGUI(Player player, String origin) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("top", null, origin, 0), 27, plugin.getConfigManager().format("&b&lTop AcidIsland"));
        List<id.alvarennation.acidIsland.island.IslandManager.IslandRanking> top = plugin.getIslandManager().getTopIslands(10);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

        for (int i = 0; i < Math.min(top.size(), slots.length); i++) {
            id.alvarennation.acidIsland.island.IslandManager.IslandRanking ranking = top.get(i);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ranking.island().getOwner());
            Material icon = i == 0 ? Material.NETHER_STAR : i < 3 ? Material.GOLD_INGOT : Material.PAPER;
            inv.setItem(slots[i], createGuiItem(
                    icon,
                    "&e#" + (i + 1) + " &f" + getOfflineName(owner),
                    "&7Level: &a" + ranking.level(),
                    "&7Value: &a" + ranking.value(),
                    "&7Theme: &e" + ranking.island().getTheme(),
                    ranking.island().isLevelScanInProgress() ? "&eSedang scanning value." : "&7Value dari cache."
            ));
        }
        if (top.isEmpty()) {
            inv.setItem(13, createGuiItem(Material.BARRIER, "&cBelum Ada Island", "&7Ranking masih kosong."));
        }

        inv.setItem(22, createGuiItem(Material.ARROW, "&eKembali", "&7Kembali ke menu sebelumnya."));
        fillFiller(inv);
        player.openInventory(inv);
    }

    public void openAdminMenu(Player player) {
        if (!player.hasPermission("acidisland.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            return;
        }

        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("admin", null), 54, plugin.getConfigManager().format("&c&lAcidIsland Admin"));
        int islandCount = plugin.getIslandManager().getAllIslands().size();
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int storyTracked = plugin.getIslandManager().getStoryStagesSnapshot().size();

        inv.setItem(4, createGuiItem(
                Material.COMMAND_BLOCK,
                "&c&lAdmin Dashboard",
                "&7Total island: &e" + islandCount,
                "&7Player online: &e" + onlineCount,
                "&7Story tracked: &e" + storyTracked,
                "&7World: &b" + plugin.getWorldManager().getAcidWorld().getName()
        ));

        inv.setItem(10, createGuiItem(Material.PLAYER_HEAD, "&ePlayer Manager", "&7Kelola island player online.", "&eKlik untuk membuka."));
        inv.setItem(11, createGuiItem(Material.NETHER_STAR, "&bTop Islands", "&7Lihat leaderboard dalam GUI.", "&eKlik untuk membuka."));
        inv.setItem(12, createGuiItem(Material.REDSTONE, "&aReload Config", "&7Flush vault, reload config/data, rebuild ore table.", "&eKlik untuk reload."));
        inv.setItem(13, createGuiItem(Material.WRITABLE_BOOK, "&aSave Data", "&7Simpan data island sekarang.", "&eKlik untuk save."));
        inv.setItem(14, createGuiItem(Material.LODESTONE, "&eSet Lobby", "&7Set lobby AcidIsland ke lokasi kamu.", "&eKlik untuk set."));
        inv.setItem(15, createGuiItem(Material.ENDER_PEARL, "&eTeleport Lobby", "&7Teleport kamu ke lobby AcidIsland.", "&eKlik untuk teleport."));
        inv.setItem(16, createGuiItem(Material.AMETHYST_SHARD, "&dStory Tools", "&7Buka player manager lalu pilih player.", "&7Stage bisa +1, +5, -1, atau reset."));

        inv.setItem(19, createGuiItem(Material.RECOVERY_COMPASS, "&aRepair Acid World", "&7Recreate/load acid world jika dihapus eksternal.", "&eKlik untuk repair."));
        inv.setItem(20, createGuiItem(Material.COMPASS, "&bTeleport Acid World", "&7Teleport admin ke acid world.", "&eKlik untuk teleport."));
        inv.setItem(21, createGuiItem(Material.BEACON, "&aRefresh Online Borders", "&7Terapkan ulang world border player online.", "&eKlik untuk refresh."));
        inv.setItem(22, createGuiItem(Material.CHEST, "&6Open Own Dashboard", "&7Buka dashboard /ai milikmu.", "&eKlik untuk membuka."));
        inv.setItem(23, createGuiItem(Material.EXPERIENCE_BOTTLE, "&aScan All Island Levels", "&7Jadwalkan scan value semua island.", "&eKlik untuk scan."));
        inv.setItem(24, createGuiItem(Material.ENDER_CHEST, "&eClose AcidIsland GUIs", "&7Tutup inventory AcidIsland yang sedang terbuka.", "&eKlik untuk tutup."));
        inv.setItem(31, createGuiItem(Material.BARRIER, "&cTutup", "&7Tutup menu admin."));

        fillFiller(inv);
        player.openInventory(inv);
    }

    public void openAdminPlayersGUI(Player admin, int page) {
        if (!admin.hasPermission("acidisland.admin")) {
            admin.sendMessage(plugin.getConfigManager().getMessage(admin, "no-permission"));
            return;
        }

        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("admin_players", null, "", page), 54, plugin.getConfigManager().format("&c&lAdmin Player Manager"));
        List<Player> players = getSortedOnlinePlayers();

        int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < players.size(); slot++) {
            Player target = players.get(start + slot);
            Island island = plugin.getIslandManager().getIslandByPlayer(target.getUniqueId());
            String islandInfo = island == null ? "&cTidak punya island" : "&a" + island.getRole(target.getUniqueId()).getDisplayName() + " &7di island &e" + getOfflineName(Bukkit.getOfflinePlayer(island.getOwner()));
            inv.setItem(slot, createGuiItem(
                    Material.PLAYER_HEAD,
                    "&e" + target.getName(),
                    islandInfo,
                    "&7Story stage: &d" + plugin.getIslandManager().getStoryStage(target.getUniqueId()),
                    "&7World: &b" + target.getWorld().getName(),
                    "&eKlik untuk kelola player ini."
            ));
        }

        if (players.isEmpty()) {
            inv.setItem(22, createGuiItem(Material.BARRIER, "&cTidak Ada Player Online", "&7Player manager menampilkan player online."));
        }
        if (page > 0) {
            inv.setItem(45, createGuiItem(Material.ARROW, "&ePrevious Page"));
        }
        inv.setItem(49, createGuiItem(Material.BARRIER, "&cKembali", "&7Kembali ke admin dashboard."));
        if (start + 45 < players.size()) {
            inv.setItem(53, createGuiItem(Material.ARROW, "&eNext Page"));
        }

        fillFiller(inv);
        admin.openInventory(inv);
    }

    public void openAdminPlayerGUI(Player admin, UUID targetUuid) {
        if (!admin.hasPermission("acidisland.admin")) {
            admin.sendMessage(plugin.getConfigManager().getMessage(admin, "no-permission"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        Player onlineTarget = target.getPlayer();
        Island island = plugin.getIslandManager().getIslandByPlayer(targetUuid);
        Island ownedIsland = plugin.getIslandManager().getIslandByOwner(targetUuid);
        String targetName = getOfflineName(target);

        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("admin_player", null, targetUuid.toString(), 0), 45, plugin.getConfigManager().format("&c&lManage " + targetName));
        inv.setItem(4, createGuiItem(
                Material.PLAYER_HEAD,
                "&e&l" + targetName,
                onlineTarget == null ? "&7Status: &cOffline" : "&7Status: &aOnline",
                island == null ? "&7Island: &cTidak ada" : "&7Island: &a" + island.getRole(targetUuid).getDisplayName(),
                ownedIsland == null ? "&7Owner island: &cTidak" : "&7Owner island: &aYa",
                "&7Story stage: &d" + plugin.getIslandManager().getStoryStage(targetUuid),
                island == null ? "&7Bank: &e-" : "&7Bank: &6" + formatMoney(island.getBankBalance())
        ));

        inv.setItem(10, createGuiItem(Material.ENDER_EYE, "&aTP ke Island", island == null ? "&cPlayer tidak punya island." : "&7Teleport admin ke home island player."));
        inv.setItem(11, createGuiItem(Material.RESPAWN_ANCHOR, "&eReset Island", "&7Hapus island lama dan buat classic island baru.", "&cButuh konfirmasi."));
        inv.setItem(12, createGuiItem(Material.TNT, "&cDelete Owned Island", ownedIsland == null ? "&cPlayer bukan owner island." : "&7Hapus island milik player.", "&cButuh konfirmasi."));
        inv.setItem(13, createGuiItem(Material.LIME_DYE, "&aStory +1", "&7Naikkan story stage player satu level."));
        inv.setItem(14, createGuiItem(Material.RED_DYE, "&cStory -1", "&7Turunkan story stage player satu level."));
        inv.setItem(15, createGuiItem(Material.EMERALD, "&aStory +5", "&7Naikkan story stage player lima level."));
        inv.setItem(16, createGuiItem(Material.BARRIER, "&cReset Story", "&7Set story stage player ke 0."));

        inv.setItem(20, createGuiItem(Material.IRON_BARS, "&eRemove From Current Island", island == null || island.isOwner(targetUuid) ? "&cHanya untuk member non-owner." : "&7Keluarkan player dari island saat ini."));
        inv.setItem(21, createGuiItem(Material.BELL, "&eSend To Lobby", onlineTarget == null ? "&cPlayer offline." : "&7Teleport player ke lobby."));
        inv.setItem(22, createGuiItem(Material.WATER_BUCKET, "&bCleanup Island Area", island == null ? "&cPlayer tidak punya island." : "&7Jadwalkan cleanup area island player."));
        inv.setItem(23, createGuiItem(Material.EXPERIENCE_BOTTLE, "&aScan Island Level", island == null ? "&cPlayer tidak punya island." : "&7Jadwalkan scan value island player."));
        inv.setItem(24, createGuiItem(Material.BEACON, "&aRefresh Target Border", onlineTarget == null ? "&cPlayer offline." : "&7Terapkan ulang border target."));
        inv.setItem(31, createGuiItem(Material.ARROW, "&eKembali", "&7Kembali ke daftar player."));

        fillFiller(inv);
        admin.openInventory(inv);
    }

    private void openAdminConfirmGUI(Player admin, UUID targetUuid, String action) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String actionName = action.equals("reset") ? "Reset Island" : "Delete Island";
        Inventory inv = Bukkit.createInventory(
                new AcidIslandHolder("admin_confirm", null, action + ":" + targetUuid, 0),
                27,
                plugin.getConfigManager().format("&c&lConfirm " + actionName)
        );
        inv.setItem(11, createGuiItem(Material.RED_WOOL, "&c&lKONFIRMASI", "&7Target: &e" + getOfflineName(target), "&7Action: &c" + actionName, "&cKlik untuk eksekusi."));
        inv.setItem(15, createGuiItem(Material.GREEN_WOOL, "&a&lBATAL", "&7Kembali ke menu player."));
        fillFiller(inv);
        admin.openInventory(inv);
    }

    // ==========================================
    // 1. Starter Island Selection GUI
    // ==========================================
    public void openStarterGUI(Player player) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("starter", null), 36, plugin.getConfigManager().format("&a&lPilih Starter Island"));

        FileConfiguration config = plugin.getConfigManager().getConfig();
        List<String> starterIds = getStarterIds();
        int[] slots = starterSlots();
        for (int i = 0; i < Math.min(starterIds.size(), slots.length); i++) {
            String starterId = starterIds.get(i);
            String path = "starters." + starterId;
            Material icon = Material.matchMaterial(config.getString(path + ".icon", "GRASS_BLOCK"));
            if (icon == null) {
                icon = Material.GRASS_BLOCK;
            }
            List<String> lore = new ArrayList<>(config.getStringList(path + ".lore"));
            lore.add(" ");
            lore.add("&8ID: " + starterId);
            lore.add("&eKlik untuk memilih starter ini.");
            inv.setItem(slots[i], createGuiItem(icon, config.getString(path + ".display-name", "&a" + starterId), lore.toArray(new String[0])));
        }

        fillFiller(inv);
        player.openInventory(inv);
    }

    private List<String> getStarterIds() {
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("starters");
        if (section == null) {
            return List.of("classic");
        }
        return section.getKeys(false).stream()
                .filter(key -> section.getConfigurationSection(key) != null)
                .toList();
    }

    private int[] starterSlots() {
        return new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    }

    // ==========================================
    // 2. Settings Category GUI
    // ==========================================
    public void openSettingsCategoryGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("main_settings", island), 27, plugin.getConfigManager().format("&9&lPengaturan Pulau"));

        inv.setItem(11, createGuiItem(Material.WRITABLE_BOOK, "&b&lBasic Settings", "&7Klik untuk membuka pengaturan dasar pulau.", "&7(Invite, PvP, Block breaking, dll.)"));
        inv.setItem(15, createGuiItem(Material.DIAMOND, "&d&lPremium Settings", "&7Klik untuk membuka pengaturan premium pulau.", "&7(Creeper damage, Fly mode, Weather lock, dll.)"));

        fillFiller(inv);
        player.openInventory(inv);
    }

    // ==========================================
    // 3. Basic Settings GUI
    // ==========================================
    public void openBasicSettingsGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("basic_settings", island), 27, plugin.getConfigManager().format("&3&lBasic Settings"));

        inv.setItem(10, createGuiItem(Material.DIAMOND_SWORD, "&ePvP Mode", getToggleLore(island.getBasicSetting("pvp"))));
        inv.setItem(11, createGuiItem(Material.ZOMBIE_HEAD, "&eMob Spawning", getToggleLore(island.getBasicSetting("mob-spawn"))));
        inv.setItem(12, createGuiItem(Material.SHEEP_SPAWN_EGG, "&eAnimal Spawning", getToggleLore(island.getBasicSetting("animal-spawn"))));
        inv.setItem(13, createGuiItem(Material.IRON_PICKAXE, "&eVisitor Block Breaking", getToggleLore(island.getBasicSetting("block-break"))));
        inv.setItem(14, createGuiItem(Material.GRASS_BLOCK, "&eVisitor Block Placing", getToggleLore(island.getBasicSetting("block-place"))));
        inv.setItem(15, createGuiItem(Material.CHEST, "&eVisitor Chest Opening", getToggleLore(island.getBasicSetting("chest-open"))));
        inv.setItem(16, createGuiItem(Material.LEVER, "&eVisitor Block Interaction", getToggleLore(island.getBasicSetting("interaction"))));

        // Back button
        inv.setItem(22, createGuiItem(Material.BARRIER, "&cKembali"));

        fillFiller(inv);
        player.openInventory(inv);
    }

    // ==========================================
    // 4. Premium Settings GUI
    // ==========================================
    public void openPremiumSettingsGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("premium_settings", island), 27, plugin.getConfigManager().format("&d&lPremium Settings"));

        inv.setItem(9, createGuiItem(Material.CREEPER_HEAD, "&bCreeper Explosion Block Damage", getToggleLore(island.getPremiumSetting("creeper-explosion"))));
        inv.setItem(10, createGuiItem(Material.CHORUS_FRUIT, "&bEnderman Block Pickup", getToggleLore(island.getPremiumSetting("enderman-grief"))));
        inv.setItem(11, createGuiItem(Material.FIRE_CHARGE, "&bFire Spread", getToggleLore(island.getPremiumSetting("fire-spread"))));
        inv.setItem(12, createGuiItem(Material.OAK_LEAVES, "&bLeaf Decay", getToggleLore(island.getPremiumSetting("leaf-decay"))));
        inv.setItem(13, createGuiItem(Material.SUNFLOWER, "&bWeather Lock (Clear)", getToggleLore(island.getPremiumSetting("weather-lock"))));
        inv.setItem(14, createGuiItem(Material.CLOCK, "&bTime Lock (Day)", getToggleLore(island.getPremiumSetting("time-lock"))));
        inv.setItem(15, createGuiItem(Material.TOTEM_OF_UNDYING, "&bKeep Inventory", getToggleLore(island.getPremiumSetting("keep-inventory"))));
        inv.setItem(16, createGuiItem(Material.FEATHER, "&bFly Mode (Members)", getToggleLore(island.getPremiumSetting("fly-mode"))));
        inv.setItem(17, createGuiItem(Material.SHIELD, "&bMob Damage to Players", getToggleLore(island.getPremiumSetting("mob-damage"))));
        inv.setItem(18, createGuiItem(Material.LEATHER_BOOTS, "&bFall Damage", getToggleLore(island.getPremiumSetting("fall-damage"))));

        // Back button
        inv.setItem(22, createGuiItem(Material.BARRIER, "&cKembali"));

        fillFiller(inv);
        player.openInventory(inv);
    }

    private String[] getToggleLore(boolean enabled) {
        return new String[]{
                enabled ? "&7Status: &a&lAKTIF" : "&7Status: &c&lNONAKTIF",
                "&7Klik untuk mengubah status."
        };
    }

    // ==========================================
    // 5. Upgrades GUI
    // ==========================================
    public void openUpgradesGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("upgrades", island), 27, plugin.getConfigManager().format("&6&lUpgrade Pulau"));

        inv.setItem(10, getUpgradeItem(island, "border", Material.BEACON, "&bUpgrade World Border"));
        inv.setItem(11, getUpgradeItem(island, "members", Material.PLAYER_HEAD, "&bUpgrade Kapasitas Anggota"));
        inv.setItem(12, getUpgradeItem(island, "vault", Material.CHEST, "&bUpgrade Penyimpanan Blok (Vault)"));
        inv.setItem(13, getUpgradeItem(island, "minions", Material.ARMOR_STAND, "&bUpgrade Limit Minion (AxMinions)"));
        inv.setItem(14, getUpgradeItem(island, "bank", Material.GOLD_INGOT, "&bUpgrade Kapasitas Bank"));
        inv.setItem(15, getUpgradeItem(island, "generator", Material.FURNACE, "&bUpgrade Cobble Generator"));

        fillFiller(inv);
        player.openInventory(inv);
    }

    private ItemStack getUpgradeItem(Island island, String type, Material mat, String name) {
        int currentLvl = island.getLevel(type);
        FileConfiguration config = plugin.getConfigManager().getConfig();
        
        int nextLvl = currentLvl + 1;
        boolean maxLvl = !config.contains("upgrades." + type + "." + nextLvl);
        
        double cost = maxLvl ? 0 : config.getDouble("upgrades." + type + "." + nextLvl + ".cost");
        
        String limitStr = "";
        String nextLimitStr = "";
        
        if (type.equals("border")) {
            limitStr = config.getInt("upgrades.border." + currentLvl + ".size") + "x" + config.getInt("upgrades.border." + currentLvl + ".size") + " blocks";
            nextLimitStr = maxLvl ? "" : config.getInt("upgrades.border." + nextLvl + ".size") + "x" + config.getInt("upgrades.border." + nextLvl + ".size") + " blocks";
        } else if (type.equals("members")) {
            limitStr = config.getInt("upgrades.members." + currentLvl + ".capacity") + " members";
            nextLimitStr = maxLvl ? "" : config.getInt("upgrades.members." + nextLvl + ".capacity") + " members";
        } else if (type.equals("vault")) {
            limitStr = config.getInt("upgrades.vault." + currentLvl + ".rows") + " baris";
            nextLimitStr = maxLvl ? "" : config.getInt("upgrades.vault." + nextLvl + ".rows") + " baris";
        } else if (type.equals("minions")) {
            limitStr = config.getInt("upgrades.minions." + currentLvl + ".limit") + " minions";
            nextLimitStr = maxLvl ? "" : config.getInt("upgrades.minions." + nextLvl + ".limit") + " minions";
        } else if (type.equals("bank")) {
            double limitVal = config.getDouble("upgrades.bank." + currentLvl + ".limit");
            limitStr = limitVal == -1 ? "Tanpa Batas" : "$" + limitVal;
            if (!maxLvl) {
                double nextVal = config.getDouble("upgrades.bank." + nextLvl + ".limit");
                nextLimitStr = nextVal == -1 ? "Tanpa Batas" : "$" + nextVal;
            }
        } else if (type.equals("generator")) {
            limitStr = "Tier " + currentLvl;
            nextLimitStr = maxLvl ? "" : "Tier " + nextLvl;
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7Level saat ini: &eTier " + currentLvl);
        lore.add("&7Kapasitas saat ini: &e" + limitStr);
        lore.add(" ");
        if (maxLvl) {
            lore.add("&c&lSUDAH MENCAPAI LEVEL MAKSIMAL");
        } else {
            lore.add("&7Level berikutnya: &6Tier " + nextLvl);
            lore.add("&7Kapasitas berikutnya: &6" + nextLimitStr);
            lore.add("&7Biaya Upgrade: &a$" + cost);
            if (type.equals("generator")) {
                int requiredStage = config.getInt("upgrades.generator." + nextLvl + ".story-stage", 0);
                if (requiredStage > 0) {
                    int currentStage = plugin.getIslandManager().getIslandStoryStage(island);
                    lore.add("&7Syarat Story: &dStage " + requiredStage + " &7(sekarang &e" + currentStage + "&7)");
                }
            }
            lore.add(" ");
            lore.add("&eKlik kiri untuk upgrade!");
        }

        return createGuiItem(mat, name, lore.toArray(new String[0]));
    }

    // ==========================================
    // 6. Delete Confirmation GUI
    // ==========================================
    public void openDeleteConfirmGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("confirm_delete", island), 27, plugin.getConfigManager().format("&c&lHapus Pulau?"));

        inv.setItem(11, createGuiItem(Material.RED_WOOL, "&c&lKONFIRMASI HAPUS", "&7Klik untuk menghapus pulau secara permanen!", "&7Semua bangunan dan harta benda akan hilang!"));
        inv.setItem(15, createGuiItem(Material.GREEN_WOOL, "&a&lBATALKAN", "&7Klik untuk kembali dan membatalkan penghapusan."));

        fillFiller(inv);
        player.openInventory(inv);
    }

    // ==========================================
    // 7. Quest GUI
    // ==========================================
    public void openQuestsGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("quests", island), 54, plugin.getConfigManager().format("&a&lIsland Quests"));
        List<String> questIds = plugin.getQuestManager().getQuestIds();

        for (int i = 0; i < Math.min(questIds.size(), inv.getSize()); i++) {
            String questId = questIds.get(i);
            boolean completed = island.hasCompletedQuest(questId);
            boolean claimable = plugin.getQuestManager().canClaim(island, questId);
            Material icon = completed ? Material.LIME_CONCRETE : claimable ? Material.EMERALD : plugin.getQuestManager().getIcon(questId);

            List<String> lore = new ArrayList<>();
            lore.addAll(plugin.getQuestManager().getDescription(questId));
            lore.add(" ");
            lore.addAll(plugin.getQuestManager().getRequirementLore(island, questId));
            double rewardMoney = plugin.getQuestManager().getRewardMoney(questId);
            if (rewardMoney > 0) {
                lore.add(" ");
                lore.add("&7Reward: &a$" + rewardMoney);
            }
            lore.add(" ");
            if (completed) {
                lore.add("&aSelesai");
            } else if (claimable) {
                lore.add("&eKlik untuk claim.");
            } else {
                lore.add("&cRequirement belum terpenuhi.");
            }

            inv.setItem(i, createGuiItem(icon, plugin.getQuestManager().getDisplayName(questId), lore.toArray(new String[0])));
        }

        fillFiller(inv);
        player.openInventory(inv);
    }

    // ==========================================
    // 8. Theme GUI
    // ==========================================
    public void openThemesGUI(Player player, Island island) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("themes", island), 27, plugin.getConfigManager().format("&b&lIsland Themes"));
        List<String> themeIds = getThemeIds();

        for (int i = 0; i < Math.min(themeIds.size(), inv.getSize()); i++) {
            String themeId = themeIds.get(i);
            String path = "themes." + themeId;
            Material icon = Material.matchMaterial(plugin.getConfigManager().getConfig().getString(path + ".icon", "GRASS_BLOCK"));
            if (icon == null) icon = Material.GRASS_BLOCK;
            boolean current = island.getTheme().equalsIgnoreCase(themeId);
            double cost = plugin.getConfigManager().getConfig().getDouble(path + ".cost", 0.0);
            inv.setItem(i, createGuiItem(
                    icon,
                    plugin.getConfigManager().getConfig().getString(path + ".display-name", themeId),
                    "&7Biome: &e" + plugin.getConfigManager().getConfig().getString(path + ".biome", "PLAINS"),
                    "&7Biaya: &a$" + cost,
                    current ? "&aSedang dipakai" : "&eKlik untuk memakai theme ini."
            ));
        }

        fillFiller(inv);
        player.openInventory(inv);
    }

    public void openVault(Player player, Island island) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int level = island.getLevel("vault");
        int rows = config.getInt("upgrades.vault." + level + ".rows", 1);
        int size = rows * 9;

        Inventory inv = openVaults.get(island.getOwner());
        if (inv == null || inv.getSize() != size) {
            inv = Bukkit.createInventory(new AcidIslandHolder("vault", island), size, plugin.getConfigManager().format("&6&lIsland Vault"));
            String base64 = island.getVaultBase64();
            if (base64 != null && !base64.isEmpty()) {
                ItemStack[] items = Island.itemStackArrayFromBase64(base64);
                if (items == null) {
                    player.sendMessage(plugin.getConfigManager().format("&cVault gagal dibaca. Data lama tidak akan ditimpa; hubungi admin."));
                    return;
                }
                for (int i = 0; i < Math.min(items.length, inv.getSize()); i++) {
                    inv.setItem(i, items[i]);
                }
            }
            openVaults.put(island.getOwner(), inv);
        }

        player.openInventory(inv);
    }

    public boolean flushVault(UUID ownerUuid) {
        Inventory inventory = openVaults.get(ownerUuid);
        if (inventory == null) {
            return true;
        }
        return flushVaultInventory(ownerUuid, inventory);
    }

    public void flushAllVaults() {
        for (UUID ownerUuid : new ArrayList<>(openVaults.keySet())) {
            flushVault(ownerUuid);
        }
    }

    public void closeAndFlushVault(UUID ownerUuid) {
        Inventory inventory = openVaults.get(ownerUuid);
        if (inventory == null) {
            return;
        }
        flushVaultInventory(ownerUuid, inventory);
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            viewer.closeInventory();
        }
        openVaults.remove(ownerUuid, inventory);
    }

    public void closeAndDiscardVault(UUID ownerUuid) {
        Inventory inventory = openVaults.remove(ownerUuid);
        if (inventory == null) {
            return;
        }
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    public void closeAllAcidIslandInventories(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AcidIslandHolder holder) {
                if (holder.getGuiType().equals("vault")) {
                    Island island = holder.getIsland();
                    if (island != null) {
                        flushVaultInventory(island.getOwner(), player.getOpenInventory().getTopInventory());
                    }
                }
                player.closeInventory();
                if (message != null && !message.isBlank()) {
                    player.sendMessage(plugin.getConfigManager().format(message));
                }
            }
        }
        openVaults.clear();
    }

    // ==========================================
    // Click Listener Handler
    // ==========================================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof AcidIslandHolder holder)) return;

        if (holder.getGuiType().equals("vault")) {
            return;
        }

        event.setCancelled(true); // Batalkan semua pemindahan item di GUI kita

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        String guiType = holder.getGuiType();
        Island island = holder.getIsland();

        if (guiType.equals("main")) {
            handleMainMenuClick(player, island, slot);
        } else if (guiType.equals("bank")) {
            handleBankClick(player, island, slot);
        } else if (guiType.equals("story")) {
            handleStoryClick(player, slot);
        } else if (guiType.equals("top")) {
            handleTopClick(player, holder.getContext(), slot);
        } else if (guiType.equals("admin")) {
            handleAdminClick(player, slot);
        } else if (guiType.equals("admin_players")) {
            handleAdminPlayersClick(player, holder.getPage(), slot);
        } else if (guiType.equals("admin_player")) {
            handleAdminPlayerClick(player, holder.getContext(), slot);
        } else if (guiType.equals("admin_confirm")) {
            handleAdminConfirmClick(player, holder.getContext(), slot);
        } else if (guiType.equals("starter")) {
            handleStarterClick(player, slot);
        } else if (guiType.equals("main_settings")) {
            handleMainSettingsClick(player, island, slot);
        } else if (guiType.equals("basic_settings")) {
            handleBasicSettingsClick(player, island, slot);
        } else if (guiType.equals("premium_settings")) {
            handlePremiumSettingsClick(player, island, slot);
        } else if (guiType.equals("upgrades")) {
            handleUpgradesClick(player, island, slot);
        } else if (guiType.equals("confirm_delete")) {
            handleDeleteConfirmClick(player, island, slot);
        } else if (guiType.equals("quests")) {
            handleQuestsClick(player, island, slot);
        } else if (guiType.equals("themes")) {
            handleThemesClick(player, island, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AcidIslandHolder holder)) return;
        if (!holder.getGuiType().equals("vault")) {
            event.setCancelled(true);
        }
    }

    private void handleMainMenuClick(Player player, Island island, int slot) {
        if (island == null) {
            switch (slot) {
                case 11 -> {
                    openStarterGUI(player);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
                case 13 -> {
                    openTopGUI(player, "main");
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
                case 15 -> teleportToLobby(player);
                case 22 -> {
                    if (player.hasPermission("acidisland.admin")) {
                        openAdminMenu(player);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    }
                }
            }
            return;
        }

        switch (slot) {
            case 10 -> teleportToIslandHome(player, island);
            case 11 -> {
                player.closeInventory();
                player.performCommand("ai sethome");
            }
            case 12 -> {
                openSettingsCategoryGUI(player, island);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 13 -> {
                openUpgradesGUI(player, island);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 14 -> {
                openVault(player, island);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 15 -> {
                openBankGUI(player, island);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 16 -> {
                openQuestsGUI(player, island);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 20 -> {
                openThemesGUI(player, island);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 21 -> {
                openStoryGUI(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 22 -> {
                openTopGUI(player, "main");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 23 -> teleportToLobby(player);
            case 24 -> {
                if (island.isOwner(player.getUniqueId())) {
                    openDeleteConfirmGUI(player, island);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                } else {
                    player.sendMessage(plugin.getConfigManager().format("&cHanya owner island yang bisa menghapus island."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
            case 31 -> {
                if (!island.canManage(player.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                plugin.getIslandManager().getIslandValue(island, true);
                player.sendMessage(plugin.getConfigManager().format("&aScan value island dijadwalkan. Buka menu lagi sebentar lagi untuk hasil terbaru."));
                openMainMenu(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            }
            case 40 -> {
                if (player.hasPermission("acidisland.admin")) {
                    openAdminMenu(player);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
            }
        }
    }

    private void handleBankClick(Player player, Island island, int slot) {
        if (island == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 10 -> depositBank(player, island, 100.0, false);
            case 11 -> depositBank(player, island, 1000.0, false);
            case 12 -> depositBank(player, island, 10000.0, false);
            case 13 -> depositBank(player, island, 0.0, true);
            case 19 -> withdrawBank(player, island, 100.0, false);
            case 20 -> withdrawBank(player, island, 1000.0, false);
            case 21 -> withdrawBank(player, island, 10000.0, false);
            case 22 -> withdrawBank(player, island, 0.0, true);
            case 31 -> {
                openMainMenu(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }

    private void handleStoryClick(Player player, int slot) {
        if (slot == 31) {
            openMainMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        int index = storySlotIndex(slot);
        if (index < 0) {
            return;
        }

        List<String> conversations = getStoryConversationIds();
        if (index >= conversations.size()) {
            return;
        }

        if (plugin.getConverseCraftHook() == null || !plugin.getConverseCraftHook().isEnabled()) {
            player.sendMessage(plugin.getConfigManager().format("&cConverseCraft belum aktif di server ini."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String conversation = conversations.get(index);
        int mappedStage = plugin.getConverseCraftHook().getMappedStoryStage(conversation);
        if (mappedStage > 0 && plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.enforce-sequential", true)) {
            int currentStage = plugin.getIslandManager().getStoryStage(player.getUniqueId());
            if (mappedStage > currentStage + 1) {
                player.sendMessage(plugin.getConfigManager().format("&cSelesaikan chapter sebelumnya dulu. Stage kamu: &e" + currentStage + "&c."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        player.closeInventory();
        boolean started = plugin.getConverseCraftHook().startConversation(player, conversation);
        if (!started) {
            player.sendMessage(plugin.getConfigManager().format("&cConversation &e" + conversation + " &cgagal dimulai atau tidak ditemukan."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void handleTopClick(Player player, String origin, int slot) {
        if (slot != 22) {
            return;
        }
        if (origin.equalsIgnoreCase("admin") && player.hasPermission("acidisland.admin")) {
            openAdminMenu(player);
        } else {
            openMainMenu(player);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleAdminClick(Player player, int slot) {
        if (!player.hasPermission("acidisland.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 10 -> {
                openAdminPlayersGUI(player, 0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 11 -> {
                openTopGUI(player, "admin");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 12 -> {
                player.closeInventory();
                player.performCommand("ai reload");
            }
            case 13 -> {
                plugin.getIslandGUI().flushAllVaults();
                plugin.getIslandManager().saveData();
                player.sendMessage(plugin.getConfigManager().format("&aData AcidIsland berhasil disimpan."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                openAdminMenu(player);
            }
            case 14 -> {
                player.closeInventory();
                player.performCommand("ai setlobby");
            }
            case 15 -> teleportToLobby(player);
            case 19 -> {
                player.closeInventory();
                player.performCommand("ai admin repairworld");
            }
            case 20 -> {
                player.closeInventory();
                player.performCommand("ai admin worldtp");
            }
            case 21 -> {
                player.closeInventory();
                player.performCommand("ai admin borders");
            }
            case 22 -> {
                openMainMenu(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            case 23 -> {
                player.closeInventory();
                player.performCommand("ai admin scan all");
            }
            case 24 -> {
                closeAllAcidIslandInventories("&eGUI AcidIsland ditutup oleh admin.");
                player.sendMessage(plugin.getConfigManager().format("&aSemua GUI AcidIsland yang terbuka sudah ditutup."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                openAdminMenu(player);
            }
            case 31 -> player.closeInventory();
        }
    }

    private void handleAdminPlayersClick(Player admin, int page, int slot) {
        if (!admin.hasPermission("acidisland.admin")) {
            admin.sendMessage(plugin.getConfigManager().getMessage(admin, "no-permission"));
            admin.closeInventory();
            return;
        }

        if (slot == 45 && page > 0) {
            openAdminPlayersGUI(admin, page - 1);
            admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot == 49) {
            openAdminMenu(admin);
            admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        List<Player> players = getSortedOnlinePlayers();
        if (slot == 53 && (page + 1) * 45 < players.size()) {
            openAdminPlayersGUI(admin, page + 1);
            admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }

        int index = page * 45 + slot;
        if (index >= players.size()) {
            return;
        }

        openAdminPlayerGUI(admin, players.get(index).getUniqueId());
        admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleAdminPlayerClick(Player admin, String context, int slot) {
        if (!admin.hasPermission("acidisland.admin")) {
            admin.sendMessage(plugin.getConfigManager().getMessage(admin, "no-permission"));
            admin.closeInventory();
            return;
        }

        UUID targetUuid = parseUuid(context);
        if (targetUuid == null) {
            admin.closeInventory();
            admin.sendMessage(plugin.getConfigManager().format("&cTarget admin GUI tidak valid."));
            return;
        }

        switch (slot) {
            case 10 -> adminTeleportToIsland(admin, targetUuid);
            case 11 -> openAdminConfirmGUI(admin, targetUuid, "reset");
            case 12 -> {
                if (plugin.getIslandManager().getIslandByOwner(targetUuid) == null) {
                    admin.sendMessage(plugin.getConfigManager().format("&cPlayer tersebut bukan owner island."));
                    admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                openAdminConfirmGUI(admin, targetUuid, "delete");
            }
            case 13 -> adjustStoryStage(admin, targetUuid, 1);
            case 14 -> adjustStoryStage(admin, targetUuid, -1);
            case 15 -> adjustStoryStage(admin, targetUuid, 5);
            case 16 -> setStoryStage(admin, targetUuid, 0);
            case 20 -> adminRemoveFromCurrentIsland(admin, targetUuid);
            case 21 -> adminSendTargetToLobby(admin, targetUuid);
            case 22 -> adminCleanupTargetIsland(admin, targetUuid);
            case 23 -> adminScanTargetIsland(admin, targetUuid);
            case 24 -> adminRefreshTargetBorder(admin, targetUuid);
            case 31 -> {
                openAdminPlayersGUI(admin, 0);
                admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        }
    }

    private void handleAdminConfirmClick(Player admin, String context, int slot) {
        String[] parts = context.split(":", 2);
        if (parts.length != 2) {
            admin.closeInventory();
            return;
        }

        UUID targetUuid = parseUuid(parts[1]);
        if (targetUuid == null) {
            admin.closeInventory();
            admin.sendMessage(plugin.getConfigManager().format("&cTarget admin GUI tidak valid."));
            return;
        }

        if (slot == 15) {
            openAdminPlayerGUI(admin, targetUuid);
            admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot != 11) {
            return;
        }

        if (parts[0].equalsIgnoreCase("reset")) {
            adminResetIsland(admin, targetUuid);
        } else if (parts[0].equalsIgnoreCase("delete")) {
            adminDeleteOwnedIsland(admin, targetUuid);
        }
    }

    private void handleStarterClick(Player player, int slot) {
        String type = "";
        int[] slots = starterSlots();
        List<String> starterIds = getStarterIds();
        for (int i = 0; i < Math.min(slots.length, starterIds.size()); i++) {
            if (slot == slots[i]) {
                type = starterIds.get(i);
                break;
            }
        }

        if (!type.isEmpty()) {
            if (plugin.getIslandManager().getIslandByPlayer(player.getUniqueId()) != null) {
                player.closeInventory();
                player.sendMessage(plugin.getConfigManager().getMessage(player, "already-has-island"));
                return;
            }
            if (!plugin.getIslandManager().canCreateIsland(player.getUniqueId())) {
                player.closeInventory();
                player.sendMessage(plugin.getConfigManager().format("&cKamu baru bisa membuat island lagi dalam " + formatDuration(plugin.getIslandManager().getCreateCooldownRemainingMillis(player.getUniqueId())) + "."));
                return;
            }
            if (plugin.getIslandManager().isIslandCreatePending(player.getUniqueId())) {
                player.closeInventory();
                player.sendMessage(plugin.getConfigManager().format("&eIsland kamu sedang dibuat. Tunggu sebentar."));
                return;
            }
            player.closeInventory();
            player.sendMessage(plugin.getConfigManager().format("&eIsland sedang dibuat. Chunk area island dimuat dulu supaya server tidak freeze."));

            plugin.getIslandManager().createIslandAsync(player.getUniqueId(), type, true).whenComplete((island, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            player.sendMessage(plugin.getConfigManager().format("&cIsland gagal dibuat: " + throwable.getMessage()));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            plugin.getLogger().warning("Failed to create island for " + player.getUniqueId() + ": " + throwable.getMessage());
                            return;
                        }

                        player.sendMessage(plugin.getConfigManager().getMessage(player, "island-created"));
                        player.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
                        plugin.getWorldManager().applyWorldBorder(player, island);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }));
        }
    }

    private void handleMainSettingsClick(Player player, Island island, int slot) {
        if (slot == 11) {
            openBasicSettingsGUI(player, island);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } else if (slot == 15) {
            openPremiumSettingsGUI(player, island);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private void handleBasicSettingsClick(Player player, Island island, int slot) {
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            player.closeInventory();
            return;
        }

        boolean toggled = false;
        switch (slot) {
            case 10 -> { island.setBasicSetting("pvp", !island.getBasicSetting("pvp")); toggled = true; }
            case 11 -> { island.setBasicSetting("mob-spawn", !island.getBasicSetting("mob-spawn")); toggled = true; }
            case 12 -> { island.setBasicSetting("animal-spawn", !island.getBasicSetting("animal-spawn")); toggled = true; }
            case 13 -> { island.setBasicSetting("block-break", !island.getBasicSetting("block-break")); toggled = true; }
            case 14 -> { island.setBasicSetting("block-place", !island.getBasicSetting("block-place")); toggled = true; }
            case 15 -> { island.setBasicSetting("chest-open", !island.getBasicSetting("chest-open")); toggled = true; }
            case 16 -> { island.setBasicSetting("interaction", !island.getBasicSetting("interaction")); toggled = true; }
            case 22 -> { openSettingsCategoryGUI(player, island); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f); return; }
        }

        if (toggled) {
            plugin.getIslandManager().saveData();
            openBasicSettingsGUI(player, island);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.3f);
        }
    }

    private void handlePremiumSettingsClick(Player player, Island island, int slot) {
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            player.closeInventory();
            return;
        }

        // Cek permission premium
        if (!player.hasPermission("acidisland.premium")) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "gui-premium-only"));
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean toggled = false;
        switch (slot) {
            case 9 -> { island.setPremiumSetting("creeper-explosion", !island.getPremiumSetting("creeper-explosion")); toggled = true; }
            case 10 -> { island.setPremiumSetting("enderman-grief", !island.getPremiumSetting("enderman-grief")); toggled = true; }
            case 11 -> { island.setPremiumSetting("fire-spread", !island.getPremiumSetting("fire-spread")); toggled = true; }
            case 12 -> { island.setPremiumSetting("leaf-decay", !island.getPremiumSetting("leaf-decay")); toggled = true; }
            case 13 -> { island.setPremiumSetting("weather-lock", !island.getPremiumSetting("weather-lock")); toggled = true; }
            case 14 -> { island.setPremiumSetting("time-lock", !island.getPremiumSetting("time-lock")); toggled = true; }
            case 15 -> { island.setPremiumSetting("keep-inventory", !island.getPremiumSetting("keep-inventory")); toggled = true; }
            case 16 -> { island.setPremiumSetting("fly-mode", !island.getPremiumSetting("fly-mode")); toggled = true; }
            case 17 -> { island.setPremiumSetting("mob-damage", !island.getPremiumSetting("mob-damage")); toggled = true; }
            case 18 -> { island.setPremiumSetting("fall-damage", !island.getPremiumSetting("fall-damage")); toggled = true; }
            case 22 -> { openSettingsCategoryGUI(player, island); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f); return; }
        }

        if (toggled) {
            plugin.getIslandManager().saveData();
            openPremiumSettingsGUI(player, island);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.3f);
        }
    }

    private void handleUpgradesClick(Player player, Island island, int slot) {
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            player.closeInventory();
            return;
        }

        String type = "";
        switch (slot) {
            case 10 -> type = "border";
            case 11 -> type = "members";
            case 12 -> type = "vault";
            case 13 -> type = "minions";
            case 14 -> type = "bank";
            case 15 -> type = "generator";
        }

        if (!type.isEmpty()) {
            int currentLvl = island.getLevel(type);
            int nextLvl = currentLvl + 1;
            FileConfiguration config = plugin.getConfigManager().getConfig();

            if (!config.contains("upgrades." + type + "." + nextLvl)) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "upgrade-max-level"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            double cost = config.getDouble("upgrades." + type + "." + nextLvl + ".cost");
            if (type.equals("generator")) {
                int requiredStage = config.getInt("upgrades.generator." + nextLvl + ".story-stage", 0);
                int currentStage = plugin.getIslandManager().getIslandStoryStage(island);
                if (currentStage < requiredStage) {
                    player.sendMessage(plugin.getConfigManager().format("&cGenerator tier ini butuh story stage &e" + requiredStage + "&c. Stage island sekarang &e" + currentStage + "&c."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }

            if (!VaultHook.hasEconomy()) {
                player.sendMessage(plugin.getConfigManager().format("&cEcon system not connected!"));
                return;
            }

            double balance = VaultHook.getEconomy().getBalance(player);
            if (balance < cost) {
                player.sendMessage(plugin.getConfigManager().getMessage(player, "upgrade-fail-money", "{cost}", String.valueOf(cost)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Bayar
            EconomyResponse response = VaultHook.getEconomy().withdrawPlayer(player, cost);
            if (!response.transactionSuccess()) {
                player.sendMessage(plugin.getConfigManager().format("&cPembayaran upgrade gagal: " + response.errorMessage));
                return;
            }
            if (type.equals("vault")) {
                plugin.getIslandGUI().closeAndFlushVault(island.getOwner());
            }
            island.setLevel(type, nextLvl);
            plugin.getIslandManager().saveData();

            // Special actions for upgrades
            if (type.equals("border")) {
                // Terapkan border baru ke player online di pulau
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Island pIsland = plugin.getIslandManager().getIslandAt(p.getLocation());
                    if (pIsland != null && pIsland.getOwner().equals(island.getOwner())) {
                        plugin.getWorldManager().applyWorldBorder(p, island);
                    }
                }
            } else if (type.equals("minions")) {
                // Integrasi AxMinions limit command jika terpasang
                if (Bukkit.getPluginManager().getPlugin("AxMinions") != null) {
                    int minionLimit = config.getInt("upgrades.minions." + nextLvl + ".limit");
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
                    if (owner.getName() == null) {
                        plugin.getLogger().warning("Skipped AxMinions limit update: unknown owner name for " + island.getOwner() + ".");
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "axminions limit set " + owner.getName() + " " + minionLimit);
                    }
                }
            }

            player.sendMessage(plugin.getConfigManager().getMessage(player, "upgrade-success", "{upgrade}", type.toUpperCase(), "{level}", String.valueOf(nextLvl)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            
            // Refresh
            openUpgradesGUI(player, island);
        }
    }

    private void handleDeleteConfirmClick(Player player, Island island, int slot) {
        if (slot == 11) {
            if (!plugin.getIslandManager().canDeleteIsland(player.getUniqueId())) {
                player.closeInventory();
                player.sendMessage(plugin.getConfigManager().format("&cKamu baru bisa menghapus island lagi dalam " + formatDuration(plugin.getIslandManager().getDeleteCooldownRemainingMillis(player.getUniqueId())) + "."));
                return;
            }
            // Confirm delete
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            
            // Delete
            Island deleted = plugin.getIslandManager().deleteIsland(player.getUniqueId());
            player.sendMessage(plugin.getConfigManager().getMessage(player, "island-deleted"));

            if (deleted != null) {
                teleportParticipantsToLobby(deleted);
            }
        } else if (slot == 15) {
            // Cancel
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private void handleQuestsClick(Player player, Island island, int slot) {
        List<String> questIds = plugin.getQuestManager().getQuestIds();
        if (slot < 0 || slot >= questIds.size()) return;

        String questId = questIds.get(slot);
        switch (plugin.getQuestManager().claim(player, island, questId)) {
            case CLAIMED -> {
                player.sendMessage(plugin.getConfigManager().format("&aQuest &e" + plugin.getQuestManager().getDisplayName(questId) + " &aberhasil diklaim."));
                openQuestsGUI(player, island);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
            case ALREADY_COMPLETED -> player.sendMessage(plugin.getConfigManager().format("&cQuest ini sudah selesai."));
            case REQUIREMENTS_NOT_MET -> player.sendMessage(plugin.getConfigManager().format("&cRequirement quest belum terpenuhi."));
            case NOT_FOUND -> player.sendMessage(plugin.getConfigManager().format("&cQuest tidak ditemukan."));
            case NO_ISLAND -> player.sendMessage(plugin.getConfigManager().getMessage(player, "no-island"));
            case REWARD_FAILED -> player.sendMessage(plugin.getConfigManager().format("&cReward quest gagal diproses. Coba lagi nanti."));
        }
    }

    private void handleThemesClick(Player player, Island island, int slot) {
        if (!island.canManage(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            player.closeInventory();
            return;
        }

        List<String> themeIds = getThemeIds();
        if (slot < 0 || slot >= themeIds.size()) return;
        changeTheme(player, island, themeIds.get(slot));
    }

    private void teleportToIslandHome(Player player, Island island) {
        player.closeInventory();
        player.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
        plugin.getWorldManager().applyWorldBorder(player, island);
        player.sendMessage(plugin.getConfigManager().getMessage(player, "teleport-home"));
    }

    private void teleportToLobby(Player player) {
        player.closeInventory();
        player.teleport(plugin.getLobbyLocation());
        player.setWorldBorder(null);
        player.sendMessage(plugin.getConfigManager().getMessage(player, "teleport-lobby"));
    }

    private void depositBank(Player player, Island island, double requestedAmount, boolean all) {
        if (!VaultHook.hasEconomy()) {
            player.sendMessage(plugin.getConfigManager().format("&cEconomy system belum tersambung!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double wallet = VaultHook.getEconomy().getBalance(player);
        double limit = getBankLimit(island);
        double capacity = limit < 0 ? wallet : Math.max(0.0, limit - island.getBankBalance());
        double amount = all ? Math.min(wallet, capacity) : requestedAmount;
        amount = roundMoney(amount);

        if (amount <= 0 || wallet < amount || (!all && limit >= 0 && island.getBankBalance() + amount > limit)) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-fail"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        EconomyResponse response = VaultHook.getEconomy().withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-fail"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        island.setBankBalance(roundMoney(island.getBankBalance() + amount));
        plugin.getIslandManager().saveData();
        player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-deposit-success", "{amount}", String.valueOf(amount)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openBankGUI(player, island);
    }

    private void withdrawBank(Player player, Island island, double requestedAmount, boolean all) {
        if (!island.canUseBank(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (!VaultHook.hasEconomy()) {
            player.sendMessage(plugin.getConfigManager().format("&cEconomy system belum tersambung!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double amount = all ? island.getBankBalance() : requestedAmount;
        amount = roundMoney(amount);
        if (amount <= 0 || island.getBankBalance() < amount) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-fail"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        EconomyResponse response = VaultHook.getEconomy().depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-fail"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        island.setBankBalance(roundMoney(island.getBankBalance() - amount));
        plugin.getIslandManager().saveData();
        player.sendMessage(plugin.getConfigManager().getMessage(player, "bank-withdraw-success", "{amount}", String.valueOf(amount)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openBankGUI(player, island);
    }

    private int storySlotIndex(int slot) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private List<Player> getSortedOnlinePlayers() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void adminTeleportToIsland(Player admin, UUID targetUuid) {
        Island island = plugin.getIslandManager().getIslandByPlayer(targetUuid);
        if (island == null) {
            admin.sendMessage(plugin.getConfigManager().format("&cPlayer tersebut tidak punya island."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        admin.closeInventory();
        admin.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
        plugin.getWorldManager().applyWorldBorder(admin, island);
        admin.sendMessage(plugin.getConfigManager().format("&aTeleport ke island " + getOfflineName(Bukkit.getOfflinePlayer(targetUuid)) + "."));
    }

    private void adjustStoryStage(Player admin, UUID targetUuid, int delta) {
        int current = plugin.getIslandManager().getStoryStage(targetUuid);
        setStoryStage(admin, targetUuid, Math.max(0, current + delta));
    }

    private void setStoryStage(Player admin, UUID targetUuid, int stage) {
        int normalized = Math.max(0, stage);
        plugin.getIslandManager().setStoryStage(targetUuid, normalized);
        String targetName = getOfflineName(Bukkit.getOfflinePlayer(targetUuid));
        admin.sendMessage(plugin.getConfigManager().format("&aStory stage " + targetName + " diset ke &e" + normalized + "&a."));
        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        if (onlineTarget != null) {
            onlineTarget.sendMessage(plugin.getConfigManager().format("&aStory progress kamu sekarang stage &e" + normalized + "&a."));
        }
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openAdminPlayerGUI(admin, targetUuid);
    }

    private void adminRemoveFromCurrentIsland(Player admin, UUID targetUuid) {
        Island island = plugin.getIslandManager().getIslandByPlayer(targetUuid);
        if (island == null || island.isOwner(targetUuid)) {
            admin.sendMessage(plugin.getConfigManager().format("&cPlayer bukan member non-owner di island mana pun."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        plugin.getIslandManager().removePlayerFromCurrentIsland(targetUuid);
        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        if (onlineTarget != null) {
            onlineTarget.setWorldBorder(null);
            onlineTarget.teleport(plugin.getLobbyLocation());
            onlineTarget.sendMessage(plugin.getConfigManager().format("&cKamu dikeluarkan dari island oleh admin."));
        }
        admin.sendMessage(plugin.getConfigManager().format("&aPlayer " + getOfflineName(Bukkit.getOfflinePlayer(targetUuid)) + " dikeluarkan dari island saat ini."));
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openAdminPlayerGUI(admin, targetUuid);
    }

    private void adminSendTargetToLobby(Player admin, UUID targetUuid) {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            admin.sendMessage(plugin.getConfigManager().format("&cPlayer target sedang offline."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        target.setWorldBorder(null);
        target.teleport(plugin.getLobbyLocation());
        target.sendMessage(plugin.getConfigManager().format("&eKamu dipindahkan ke lobby oleh admin."));
        admin.sendMessage(plugin.getConfigManager().format("&a" + target.getName() + " dipindahkan ke lobby."));
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openAdminPlayerGUI(admin, targetUuid);
    }

    private void adminCleanupTargetIsland(Player admin, UUID targetUuid) {
        Island island = plugin.getIslandManager().getIslandByPlayer(targetUuid);
        if (island == null) {
            admin.sendMessage(plugin.getConfigManager().format("&cPlayer tersebut tidak punya island."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        plugin.getWorldManager().scheduleIslandCleanup(island);
        admin.sendMessage(plugin.getConfigManager().format("&aCleanup island " + getOfflineName(Bukkit.getOfflinePlayer(targetUuid)) + " dijadwalkan."));
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openAdminPlayerGUI(admin, targetUuid);
    }

    private void adminScanTargetIsland(Player admin, UUID targetUuid) {
        Island island = plugin.getIslandManager().getIslandByPlayer(targetUuid);
        if (island == null) {
            admin.sendMessage(plugin.getConfigManager().format("&cPlayer tersebut tidak punya island."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        plugin.getWorldManager().scheduleIslandValueScan(island);
        admin.sendMessage(plugin.getConfigManager().format("&aScan level island " + getOfflineName(Bukkit.getOfflinePlayer(targetUuid)) + " dijadwalkan."));
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openAdminPlayerGUI(admin, targetUuid);
    }

    private void adminRefreshTargetBorder(Player admin, UUID targetUuid) {
        Player target = Bukkit.getPlayer(targetUuid);
        Island island = plugin.getIslandManager().getIslandByPlayer(targetUuid);
        if (target == null || island == null) {
            admin.sendMessage(plugin.getConfigManager().format("&cTarget harus online dan punya island."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        plugin.getWorldManager().applyWorldBorder(target, island);
        admin.sendMessage(plugin.getConfigManager().format("&aWorld border " + target.getName() + " direfresh."));
        target.sendMessage(plugin.getConfigManager().format("&eWorld border island kamu direfresh oleh admin."));
        admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        openAdminPlayerGUI(admin, targetUuid);
    }

    private void adminResetIsland(Player admin, UUID targetUuid) {
        plugin.getIslandManager().removePlayerFromCurrentIsland(targetUuid);
        Island oldIsland = plugin.getIslandManager().deleteIsland(targetUuid, true, false);
        if (oldIsland != null) {
            teleportParticipantsToLobby(oldIsland);
        }

        admin.closeInventory();
        admin.sendMessage(plugin.getConfigManager().format("&eReset island " + getOfflineName(Bukkit.getOfflinePlayer(targetUuid)) + " sedang diproses. Chunk area island dimuat async dulu."));
        plugin.getIslandManager().createIslandAsync(targetUuid, "classic", false).whenComplete((newIsland, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        admin.sendMessage(plugin.getConfigManager().format("&cReset island gagal: " + throwable.getMessage()));
                        admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        plugin.getLogger().warning("Failed to reset island for " + targetUuid + ": " + throwable.getMessage());
                        return;
                    }

                    Player onlineTarget = Bukkit.getPlayer(targetUuid);
                    if (onlineTarget != null) {
                        onlineTarget.teleport(newIsland.getHome(plugin.getWorldManager().getAcidWorld()));
                        plugin.getWorldManager().applyWorldBorder(onlineTarget, newIsland);
                        onlineTarget.sendMessage(plugin.getConfigManager().format("&aIsland kamu sudah direset oleh admin."));
                    }

                    admin.sendMessage(plugin.getConfigManager().format("&aIsland milik " + getOfflineName(Bukkit.getOfflinePlayer(targetUuid)) + " sudah direset."));
                    admin.playSound(admin.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    openAdminPlayerGUI(admin, targetUuid);
                }));
    }

    private void adminDeleteOwnedIsland(Player admin, UUID targetUuid) {
        Island deleted = plugin.getIslandManager().deleteIsland(targetUuid, true);
        if (deleted == null) {
            admin.sendMessage(plugin.getConfigManager().format("&cPlayer tersebut tidak punya island sebagai owner."));
            admin.playSound(admin.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            openAdminPlayerGUI(admin, targetUuid);
            return;
        }

        teleportParticipantsToLobby(deleted);
        admin.sendMessage(plugin.getConfigManager().format("&aIsland milik " + getOfflineName(Bukkit.getOfflinePlayer(targetUuid)) + " dihapus dan cleanup dijadwalkan."));
        admin.playSound(admin.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        openAdminPlayerGUI(admin, targetUuid);
    }

    private void changeTheme(Player player, Island island, String themeId) {
        String normalized = themeId.toLowerCase(Locale.ROOT);
        String path = "themes." + normalized;
        if (plugin.getConfigManager().getConfig().getConfigurationSection(path) == null) {
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

        double cost = plugin.getConfigManager().getConfig().getDouble(path + ".cost", 0.0);
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
            player.sendMessage(plugin.getConfigManager().format("&aTheme island diubah ke &e" + plugin.getConfigManager().getConfig().getString(path + ".display-name", normalized) + "&a."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            openThemesGUI(player, island);
        } else {
            player.sendMessage(plugin.getConfigManager().format("&cTheme gagal diterapkan. Cek nama biome di config."));
        }
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

    private List<String> getStoryConversationIds() {
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("integrations.conversecraft.story-stage-conversations");
        List<String> ids = new ArrayList<>();
        if (section == null) {
            return ids;
        }
        ids.addAll(section.getKeys(false));
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private double getBankLimit(Island island) {
        int bankLevel = island.getLevel("bank");
        return plugin.getConfigManager().getConfig().getDouble("upgrades.bank." + bankLevel + ".limit", 10000.0);
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatMoney(double value) {
        return "$" + String.format(Locale.US, "%,.2f", value);
    }

    private String getOfflineName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AcidIslandHolder holder) {
            if (holder.getGuiType().equals("vault")) {
                Island island = holder.getIsland();
                if (island != null) {
                    Inventory inventory = event.getInventory();
                    flushVaultInventory(island.getOwner(), inventory);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (inventory.getViewers().isEmpty()) {
                            openVaults.remove(island.getOwner(), inventory);
                        }
                    });
                }
            }
        }
    }

    private boolean flushVaultInventory(UUID ownerUuid, Inventory inventory) {
        Island currentIsland = plugin.getIslandManager().getIslandByOwner(ownerUuid);
        if (currentIsland == null) {
            return false;
        }

        String base64 = Island.itemStackArrayToBase64(inventory.getContents());
        if (base64 == null) {
            plugin.getLogger().warning("Skipping vault save for " + ownerUuid + " because serialization failed.");
            return false;
        }

        currentIsland.setVaultBase64(base64);
        plugin.getIslandManager().saveData();
        return true;
    }
}
