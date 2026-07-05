package id.alvarennation.acidIsland.gui;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.hooks.VaultHook;
import id.alvarennation.acidIsland.island.Island;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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

        public AcidIslandHolder(String guiType, Island island) {
            this.guiType = guiType;
            this.island = island;
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
    // 1. Starter Island Selection GUI
    // ==========================================
    public void openStarterGUI(Player player) {
        Inventory inv = Bukkit.createInventory(new AcidIslandHolder("starter", null), 27, plugin.getConfigManager().format("&a&lPilih Starter Island"));

        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Classic
        Material classicIcon = Material.matchMaterial(config.getString("starters.classic.icon", "OAK_SAPLING"));
        if (classicIcon == null) classicIcon = Material.OAK_SAPLING;
        List<String> classicLore = config.getStringList("starters.classic.lore");
        inv.setItem(11, createGuiItem(classicIcon, config.getString("starters.classic.display-name", "&aClassic Island"), classicLore.toArray(new String[0])));

        // Desert
        Material desertIcon = Material.matchMaterial(config.getString("starters.desert.icon", "SAND"));
        if (desertIcon == null) desertIcon = Material.SAND;
        List<String> desertLore = config.getStringList("starters.desert.lore");
        inv.setItem(13, createGuiItem(desertIcon, config.getString("starters.desert.display-name", "&eDesert Island"), desertLore.toArray(new String[0])));

        // Nether
        Material netherIcon = Material.matchMaterial(config.getString("starters.nether.icon", "NETHERRACK"));
        if (netherIcon == null) netherIcon = Material.NETHERRACK;
        List<String> netherLore = config.getStringList("starters.nether.lore");
        inv.setItem(15, createGuiItem(netherIcon, config.getString("starters.nether.display-name", "&cNether Island"), netherLore.toArray(new String[0])));

        fillFiller(inv);
        player.openInventory(inv);
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
                for (int i = 0; i < Math.min(items.length, inv.getSize()); i++) {
                    inv.setItem(i, items[i]);
                }
            }
            openVaults.put(island.getOwner(), inv);
        }

        player.openInventory(inv);
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

        if (guiType.equals("starter")) {
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

    private void handleStarterClick(Player player, int slot) {
        String type = "";
        if (slot == 11) type = "classic";
        else if (slot == 13) type = "desert";
        else if (slot == 15) type = "nether";

        if (!type.isEmpty()) {
            if (!plugin.getIslandManager().canCreateIsland(player.getUniqueId())) {
                player.closeInventory();
                player.sendMessage(plugin.getConfigManager().format("&cKamu baru bisa membuat island lagi dalam " + formatDuration(plugin.getIslandManager().getCreateCooldownRemainingMillis(player.getUniqueId())) + "."));
                return;
            }
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // Create island
            Island island = plugin.getIslandManager().createIsland(player.getUniqueId(), type);
            player.sendMessage(plugin.getConfigManager().getMessage(player, "island-created"));
            
            // Teleport to home
            player.teleport(island.getHome(plugin.getWorldManager().getAcidWorld()));
            plugin.getWorldManager().applyWorldBorder(player, island);
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
                int minionLimit = config.getInt("upgrades.minions." + nextLvl + ".limit");
                OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner());
                String targetName = owner.getName() == null ? player.getName() : owner.getName();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "axminions limit set " + targetName + " " + minionLimit);
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
                    String base64 = Island.itemStackArrayToBase64(inventory.getContents());
                    island.setVaultBase64(base64);
                    plugin.getIslandManager().saveData();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (inventory.getViewers().isEmpty()) {
                            openVaults.remove(island.getOwner(), inventory);
                        }
                    });
                }
            }
        }
    }
}
