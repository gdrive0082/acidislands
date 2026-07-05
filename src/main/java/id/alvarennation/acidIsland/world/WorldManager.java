package id.alvarennation.acidIsland.world;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class WorldManager {

    private final AcidIsland plugin;
    private World acidWorld;
    private final IslandValueScanner islandValueScanner;

    public WorldManager(AcidIsland plugin) {
        this.plugin = plugin;
        this.islandValueScanner = new IslandValueScanner(plugin);
    }

    public void initWorld() {
        String worldName = plugin.getConfigManager().getConfig().getString("world-name", "acid_island_world");
        int waterHeight = plugin.getConfigManager().getConfig().getInt("acid-water.height", 62);

        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidWorldGenerator(waterHeight));
        creator.environment(World.Environment.NORMAL);
        
        this.acidWorld = creator.createWorld();
        if (this.acidWorld != null) {
            this.acidWorld.setStorm(false);
            this.acidWorld.setThundering(false);
            this.acidWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            id.alvarennation.acidIsland.hooks.MultiverseHook.registerWorld(worldName);
        }
    }

    public World getAcidWorld() {
        if (acidWorld == null) {
            initWorld();
        }
        return acidWorld;
    }

    public void generateStarterIsland(int cx, int cz, String type) {
        World world = getAcidWorld();
        int islandY = 75; // Ketinggian pulau starter

        Material surfaceMat = Material.GRASS_BLOCK;
        Material subMat = Material.DIRT;
        Material fenceMat = Material.OAK_FENCE;
        TreeType treeType = TreeType.TREE;
        boolean netherStarter = type.equalsIgnoreCase("nether");

        if (type.equalsIgnoreCase("desert")) {
            surfaceMat = Material.SAND;
            subMat = Material.SANDSTONE;
            fenceMat = Material.ACACIA_FENCE;
            treeType = TreeType.ACACIA;
        } else if (netherStarter) {
            surfaceMat = Material.NETHERRACK;
            subMat = Material.NETHERRACK;
            fenceMat = Material.NETHER_BRICK_FENCE;
            treeType = TreeType.CRIMSON_FUNGUS;
        }

        // 1. Generate 10x10 Platform
        for (int x = cx - 5; x <= cx + 4; x++) {
            for (int z = cz - 5; z <= cz + 4; z++) {
                // Layer atas (Y=75)
                world.getBlockAt(x, islandY, z).setType(surfaceMat);
                // Layer bawah (Y=74, Y=73)
                world.getBlockAt(x, islandY - 1, z).setType(subMat);
                world.getBlockAt(x, islandY - 2, z).setType(subMat);
            }
        }

        // 2. Tiang penyangga di tengah bawah sampai water level (Y=62)
        for (int y = islandY - 3; y >= 62; y--) {
            world.getBlockAt(cx, y, cz).setType(subMat);
        }

        // 3. Bedrock di bawah tengah (Y=61)
        world.getBlockAt(cx, 61, cz).setType(Material.BEDROCK);

        // 4. Generate Tree di tengah (Y=76)
        if (netherStarter) {
            world.getBlockAt(cx, islandY, cz).setType(Material.CRIMSON_NYLIUM);
        }
        Location treeLoc = new Location(world, cx, islandY + 1, cz);
        boolean treeGenerated = world.generateTree(treeLoc, treeType);
        if (!treeGenerated && netherStarter) {
            generateFallbackCrimsonFungus(cx, islandY + 1, cz);
        }

        // 5. Generate Chest starter (Y=76)
        Location chestLoc = new Location(world, cx + 2, islandY + 1, cz + 2);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            fillStarterChest(inv, type);
        }

        // 6. Generate Sheep tied to fence
        Location fenceLoc = new Location(world, cx - 2, islandY + 1, cz - 2);
        Block fenceBlock = world.getBlockAt(fenceLoc);
        fenceBlock.setType(fenceMat);

        Location sheepLoc = new Location(world, cx - 2, islandY + 2, cz - 2);
        Sheep sheep = (Sheep) world.spawnEntity(sheepLoc, EntityType.SHEEP);
        sheep.setColor(DyeColor.WHITE);

        // Pasang tali (leash) ke fence menggunakan LeashHitch
        try {
            LeashHitch hitch = world.spawn(fenceLoc, LeashHitch.class);
            sheep.setLeashHolder(hitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Failed to attach starter sheep leash at " + cx + ", " + cz + ": " + ex.getMessage());
        }

        // 7. Generate Mini Ocean Monument di bawah air (Y=40)
        generateMiniMonument(cx, 40, cz);
    }

    private void generateFallbackCrimsonFungus(int cx, int baseY, int cz) {
        World world = getAcidWorld();
        for (int y = baseY; y <= baseY + 5; y++) {
            world.getBlockAt(cx, y, cz).setType(Material.CRIMSON_STEM);
        }
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = baseY + 4; y <= baseY + 6; y++) {
                    if (Math.abs(x - cx) + Math.abs(z - cz) <= 3) {
                        world.getBlockAt(x, y, z).setType(Material.NETHER_WART_BLOCK);
                    }
                }
            }
        }
        world.getBlockAt(cx, baseY + 5, cz).setType(Material.CRIMSON_STEM);
    }

    private void fillStarterChest(Inventory inv, String type) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "starters." + type.toLowerCase() + ".chest-items";
        List<String> itemsList = config.getStringList(path);

        for (String itemStr : itemsList) {
            ItemStack item = id.alvarennation.acidIsland.hooks.MMOItemsHook.getItem(itemStr);
            if (item != null) {
                inv.addItem(item);
            }
        }
    }

    private void generateMiniMonument(int cx, int cy, int cz) {
        World world = getAcidWorld();
        // Mini Ocean Monument berukuran 5x5x5 terbuat dari Prismarine
        // Dinding luar Prismarine, dalam berisi chest harta karun
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int y = cy; y <= cy + 4; y++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean edge = (x == cx - 2 || x == cx + 2 || y == cy || y == cy + 4 || z == cz - 2 || z == cz + 2);
                    if (edge) {
                        // Selubung luar
                        Material blockType = Material.PRISMARINE;
                        if (y == cy || y == cy + 4) blockType = Material.PRISMARINE_BRICKS;
                        world.getBlockAt(x, y, z).setType(blockType);
                    } else {
                        // Rongga udara/kosong di dalam agar air beracun tidak masuk ke area chest
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }

        // Pintu masuk kecil di atas (Y=cy+4) atau samping agar player bisa masuk
        world.getBlockAt(cx, cy + 4, cz).setType(Material.DARK_PRISMARINE); // Penanda pintu masuk (tinggal dihancurkan)
        
        // Letakkan Chest Harta Karun di tengah dalam monument
        Location chestLoc = new Location(world, cx, cy + 1, cz);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            
            // 1. Water Breathing Potion (Sangat krusial untuk bertahan di air)
            ItemStack potion = new ItemStack(Material.POTION);
            PotionMeta meta = (PotionMeta) potion.getItemMeta();
            if (meta != null) {
                meta.addCustomEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 60 * 8, 0), true);
                meta.setDisplayName("§bWater Breathing Potion (Acid Immunity)");
                potion.setItemMeta(meta);
            }
            inv.addItem(potion);

            // 2. Harta karun lainnya (Gold nugget, Prismarine Crystals, Heart of the Sea, etc.)
            inv.addItem(new ItemStack(Material.GOLD_NUGGET, 8));
            inv.addItem(new ItemStack(Material.PRISMARINE_SHARD, 4));
            inv.addItem(new ItemStack(Material.PRISMARINE_CRYSTALS, 4));
            
            Random r = new Random();
            if (r.nextBoolean()) {
                inv.addItem(new ItemStack(Material.DIAMOND, 1));
            } else {
                inv.addItem(new ItemStack(Material.GOLD_INGOT, 2));
            }
        }
    }

    /**
     * Apply or update WorldBorder per-player.
     */
    public void applyWorldBorder(Player player, Island island) {
        if (island == null) {
            player.setWorldBorder(null);
            return;
        }

        int level = island.getLevel("border");
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int size = config.getInt("upgrades.border." + level + ".size", 50);

        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(island.getX(), island.getZ());
        border.setSize(size);
        border.setDamageAmount(0.2);
        border.setDamageBuffer(2.0);
        player.setWorldBorder(border);
    }

    public void scheduleIslandCleanup(Island island) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        if (!config.getBoolean("cleanup.enabled", true)) {
            return;
        }

        World world = getAcidWorld();
        int borderSize = plugin.getIslandManager().getBorderSize(island);
        int padding = config.getInt("cleanup.padding", 8);
        int half = (int) Math.ceil(borderSize / 2.0) + padding;
        int minX = island.getX() - half;
        int maxX = island.getX() + half;
        int minZ = island.getZ() - half;
        int maxZ = island.getZ() + half;
        int waterHeight = config.getInt("acid-water.height", 62);
        int minY = Math.max(world.getMinHeight(), config.getInt("cleanup.min-y", world.getMinHeight()));
        int maxY = Math.min(world.getMaxHeight() - 1, config.getInt("cleanup.max-y", 160));
        int columnsPerTick = Math.max(1, config.getInt("cleanup.columns-per-tick", 12));

        removeNonPlayerEntities(world, island.getX(), island.getZ(), half, minY, maxY);

        new BukkitRunnable() {
            private int x = minX;
            private int z = minZ;

            @Override
            public void run() {
                int processed = 0;
                while (processed < columnsPerTick) {
                    resetColumn(world, x, z, minY, maxY, waterHeight);
                    processed++;

                    z++;
                    if (z > maxZ) {
                        z = minZ;
                        x++;
                    }
                    if (x > maxX) {
                        cancel();
                        plugin.getLogger().info("Finished cleaning island at " + island.getX() + ", " + island.getZ() + ".");
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void scheduleIslandValueScan(Island island) {
        islandValueScanner.enqueue(island);
    }

    public void cancelIslandValueScans() {
        islandValueScanner.cancelAll();
    }

    public boolean applyIslandTheme(Island island, String themeId) {
        Biome biome = getThemeBiome(themeId);
        if (biome == null) {
            return false;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();
        World world = getAcidWorld();
        int borderSize = plugin.getIslandManager().getBorderSize(island);
        int half = borderSize / 2;
        int columnsPerTick = Math.max(1, config.getInt("themes.columns-per-tick", 32));
        int minX = island.getX() - half;
        int maxX = island.getX() + half;
        int minZ = island.getZ() - half;
        int maxZ = island.getZ() + half;

        new BukkitRunnable() {
            private int x = minX;
            private int z = minZ;

            @Override
            public void run() {
                int processed = 0;
                while (processed < columnsPerTick) {
                    world.setBiome(x, z, biome);
                    processed++;

                    z++;
                    if (z > maxZ) {
                        z = minZ;
                        x++;
                    }
                    if (x > maxX) {
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        island.setTheme(themeId);
        island.invalidateLevelCache();
        plugin.getIslandManager().saveData();
        return true;
    }

    public boolean isThemeValid(String themeId) {
        return getThemeBiome(themeId) != null;
    }

    private void resetColumn(World world, int x, int z, int minY, int maxY, int waterHeight) {
        int worldMin = world.getMinHeight();
        for (int y = minY; y <= maxY; y++) {
            Material target;
            if (y == worldMin) {
                target = Material.BEDROCK;
            } else if (y <= waterHeight) {
                target = Material.WATER;
            } else {
                target = Material.AIR;
            }
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() != target) {
                block.setType(target, false);
            }
        }
    }

    private void removeNonPlayerEntities(World world, int centerX, int centerZ, int half, int minY, int maxY) {
        Location center = new Location(world, centerX, (minY + maxY) / 2.0, centerZ);
        double yRadius = Math.max(1, (maxY - minY) / 2.0);
        for (Entity entity : world.getNearbyEntities(center, half, yRadius, half)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    private Biome getThemeBiome(String themeId) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String path = "themes." + themeId;
        if (config.getConfigurationSection(path) == null) {
            return null;
        }

        String biomeName = config.getString(path + ".biome", "PLAINS");
        try {
            return Biome.valueOf(biomeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid biome '" + biomeName + "' for theme " + themeId + ".");
            return null;
        }
    }
}
