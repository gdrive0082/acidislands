package id.alvarennation.acidIsland.world;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.SeaPickle;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager {

    private final AcidIsland plugin;
    private World acidWorld;
    private final IslandValueScanner islandValueScanner;
    private final Set<BukkitTask> cleanupTasks = ConcurrentHashMap.newKeySet();
    private final Set<BukkitTask> biomeTasks = ConcurrentHashMap.newKeySet();
    private BukkitTask storageWatchdogTask;
    private volatile boolean shuttingDown = false;

    public WorldManager(AcidIsland plugin) {
        this.plugin = plugin;
        this.islandValueScanner = new IslandValueScanner(plugin);
    }

    public void shutdown() {
        shuttingDown = true;
        if (storageWatchdogTask != null) {
            storageWatchdogTask.cancel();
            storageWatchdogTask = null;
        }
        cancelScheduledWorldTasks();
        cancelIslandValueScans();
    }

    public void initWorld() {
        String worldName = plugin.getConfigManager().getAcidWorldName();
        int waterHeight = plugin.getConfigManager().getConfig().getInt("acid-water.height", 62);

        ensureWorldStorageDirectories(worldName);

        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(new VoidWorldGenerator(waterHeight));
        creator.environment(World.Environment.NORMAL);
        
        this.acidWorld = creator.createWorld();
        if (this.acidWorld != null) {
            ensureWorldStorageDirectories(this.acidWorld);
            startStorageWatchdog();
            this.acidWorld.setStorm(false);
            this.acidWorld.setThundering(false);
            this.acidWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            id.alvarennation.acidIsland.hooks.MultiverseHook.registerWorld(worldName);
        }
    }

    public World getAcidWorld() {
        if (acidWorld == null) {
            initWorld();
        } else {
            ensureWorldStorageDirectories(acidWorld);
        }
        return acidWorld;
    }

    private void startStorageWatchdog() {
        if (!plugin.getConfigManager().getConfig().getBoolean("world-storage.repair-missing-directories", true)) {
            return;
        }
        if (storageWatchdogTask != null && !storageWatchdogTask.isCancelled()) {
            return;
        }
        long interval = Math.max(100L, plugin.getConfigManager().getConfig().getLong("world-storage.repair-interval-ticks", 1200L));
        storageWatchdogTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shuttingDown || !plugin.isEnabled()) {
                    cancel();
                    return;
                }
                World world = acidWorld;
                if (world != null) {
                    ensureWorldStorageDirectories(world);
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void ensureWorldStorageDirectories(String worldName) {
        ensureWorldStorageDirectories(new File(Bukkit.getWorldContainer(), worldName), worldName);
    }

    private void ensureWorldStorageDirectories(World world) {
        ensureWorldStorageDirectories(world.getWorldFolder(), world.getName());
    }

    private void ensureWorldStorageDirectories(File worldDirectory, String worldName) {
        boolean repaired = false;
        repaired |= ensureDirectory(worldDirectory, "world folder", worldName);
        repaired |= ensureDirectory(new File(worldDirectory, "data"), "world data folder", worldName);
        repaired |= ensureDirectory(new File(worldDirectory, "region"), "world region folder", worldName);
        repaired |= ensureDirectory(new File(worldDirectory, "entities"), "world entities folder", worldName);
        repaired |= ensureDirectory(new File(worldDirectory, "poi"), "world POI folder", worldName);
        if (repaired) {
            plugin.getLogger().warning("Repaired missing storage directories for world '" + worldName + "'. If this happened while the server was running, restore the world folder from backup to recover lost chunk files.");
        }
    }

    private boolean ensureDirectory(File directory, String label, String worldName) {
        if (directory.isDirectory()) {
            return false;
        }
        if (directory.exists()) {
            plugin.getLogger().severe("Cannot repair " + label + " for world '" + worldName + "' because path is not a directory: " + directory.getPath());
            return false;
        }
        if (directory.mkdirs() || directory.isDirectory()) {
            return true;
        }
        plugin.getLogger().severe("Could not create " + label + " for world '" + worldName + "': " + directory.getPath());
        return false;
    }

    public void generateStarterIsland(int cx, int cz, String type) {
        World world = getAcidWorld();
        int waterHeight = plugin.getConfigManager().getConfig().getInt("acid-water.height", 62);
        int islandY = getStarterIslandY();
        String starterType = type == null ? "classic" : type.toLowerCase(Locale.ROOT);
        StarterPalette palette = StarterPalette.from(starterType);
        Random random = new Random((((long) cx) << 32) ^ cz ^ starterType.hashCode());

        clearStarterAir(world, cx, islandY, cz, waterHeight);
        generateOrganicIslandBase(world, cx, islandY, cz, waterHeight, palette);
        generateNaturalSupports(world, cx, islandY, cz, waterHeight, palette);
        decorateStarterIsland(world, cx, islandY, cz, starterType, palette, random);

        Location chestLoc = new Location(world, cx + 3, islandY + 1, cz + 2);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            fillStarterChest(inv, type);
        }

        Location fenceLoc = new Location(world, cx - 3, islandY + 1, cz - 2);
        Block fenceBlock = world.getBlockAt(fenceLoc);
        fenceBlock.setType(palette.fence(), false);

        Location sheepLoc = new Location(world, cx - 3, islandY + 2, cz - 2);
        Sheep sheep = (Sheep) world.spawnEntity(sheepLoc, EntityType.SHEEP);
        sheep.setColor(DyeColor.WHITE);

        try {
            LeashHitch hitch = world.spawn(fenceLoc, LeashHitch.class);
            sheep.setLeashHolder(hitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Failed to attach starter sheep leash at " + cx + ", " + cz + ": " + ex.getMessage());
        }

        generateRandomUnderIslandStructure(cx, waterHeight, cz, random);
    }

    public CompletableFuture<Void> preloadStarterIslandChunks(int cx, int cz) {
        World world = getAcidWorld();
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int shipX = cx + config.getInt("starter-island.shipwreck-offset-x", 0);
        int shipZ = cz + config.getInt("starter-island.shipwreck-offset-z", 0);
        int minChunkX = blockToChunk(Math.min(cx - 12, shipX - 18));
        int maxChunkX = blockToChunk(Math.max(cx + 12, shipX + 18));
        int minChunkZ = blockToChunk(Math.min(cz - 8, shipZ - 10));
        int maxChunkZ = blockToChunk(Math.max(cz + 8, shipZ + 10));

        List<CompletableFuture<Chunk>> chunkLoads = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunkLoads.add(world.getChunkAtAsync(chunkX, chunkZ, true));
            }
        }
        return CompletableFuture.allOf(chunkLoads.toArray(CompletableFuture[]::new));
    }

    private int blockToChunk(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, 16);
    }

    public int getStarterIslandY() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int waterHeight = config.getInt("acid-water.height", 62);
        int offset = Math.max(0, config.getInt("starter-island.surface-offset-above-water", 0));
        return waterHeight + offset;
    }

    public Location getStarterIslandHome(int cx, int cz) {
        return new Location(getAcidWorld(), cx + 0.5, getStarterIslandY() + 1.0, cz - 2.5, 0.0f, 0.0f);
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
        String starterType = type == null ? "classic" : type.toLowerCase(Locale.ROOT);
        String path = "starters." + starterType + ".chest-items";
        List<String> itemsList = config.getStringList(path);

        for (String itemStr : itemsList) {
            ItemStack item = id.alvarennation.acidIsland.hooks.MMOItemsHook.getItem(itemStr);
            if (item != null) {
                inv.addItem(item);
            }
        }
    }

    private void clearStarterAir(World world, int cx, int islandY, int cz, int waterHeight) {
        for (int x = cx - 7; x <= cx + 7; x++) {
            for (int z = cz - 6; z <= cz + 6; z++) {
                for (int y = waterHeight + 1; y <= islandY + 11; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void generateOrganicIslandBase(World world, int cx, int islandY, int cz, int waterHeight, StarterPalette palette) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (!isStarterIslandColumn(cx + dx, cz + dz, dx, dz)) {
                    continue;
                }

                int depth = starterColumnDepth(dx, dz);
                if (coordinateNoise(cx + dx, cz + dz, 17) > 0.72) {
                    depth++;
                }

                Material top = chooseSurfaceMaterial(cx + dx, cz + dz, palette);
                setBlock(world, cx + dx, islandY, cz + dz, top);
                for (int y = islandY - 1; y >= islandY - depth; y--) {
                    Material body = y <= islandY - depth + 1 ? palette.underside() : palette.subsurface();
                    setBlock(world, cx + dx, y, cz + dz, body);
                }
            }
        }
    }

    private void generateNaturalSupports(World world, int cx, int islandY, int cz, int waterHeight, StarterPalette palette) {
        generateSupport(world, cx, islandY, cz, waterHeight, palette, 0, 0, 2);
        generateSupport(world, cx, islandY, cz, waterHeight + 5, palette, -2, 2, 1);
        generateSupport(world, cx, islandY, cz, waterHeight + 6, palette, 2, -2, 1);
    }

    private void generateSupport(World world, int cx, int islandY, int cz, int bottomY, StarterPalette palette, int offsetX, int offsetZ, int topRadius) {
        int topY = islandY - 2;
        for (int y = topY; y >= bottomY; y--) {
            double progress = (topY - y) / (double) Math.max(1, topY - bottomY);
            int radius = progress < 0.24 ? topRadius : progress < 0.62 ? 1 : 0;
            int driftX = offsetX + (int) Math.round((coordinateNoise(cx + offsetX, cz + y, 41) - 0.5) * 1.2);
            int driftZ = offsetZ + (int) Math.round((coordinateNoise(cx + y, cz + offsetZ, 43) - 0.5) * 1.2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= radius + 1) {
                        setBlock(world, cx + driftX + dx, y, cz + driftZ + dz, palette.underside());
                    }
                }
            }
        }
    }

    private void decorateStarterIsland(World world, int cx, int islandY, int cz, String type, StarterPalette palette, Random random) {
        Location treeLoc = new Location(world, cx + 1, islandY + 1, cz + 1);
        if (palette.nether()) {
            setBlock(world, cx + 1, islandY, cz + 1, Material.CRIMSON_NYLIUM);
        }
        boolean treeGenerated = world.generateTree(treeLoc, palette.treeType());
        if (!treeGenerated) {
            if (palette.nether()) {
                generateFallbackCrimsonFungus(cx + 1, islandY + 1, cz + 1);
            } else if (type.equals("desert")) {
                generateFallbackAcaciaTree(world, cx + 1, islandY + 1, cz + 1);
            } else {
                generateFallbackOakTree(world, cx + 1, islandY + 1, cz + 1);
            }
        }

        for (int i = 0; i < 14; i++) {
            int dx = random.nextInt(11) - 5;
            int dz = random.nextInt(9) - 4;
            if (!isStarterIslandColumn(cx + dx, cz + dz, dx, dz)) {
                continue;
            }
            placeStarterDecoration(world, cx + dx, islandY + 1, cz + dz, type, random);
        }

        if (type.equals("desert")) {
            placeCactus(world, cx - 4, islandY + 1, cz + 1, 2);
            setBlock(world, cx + 2, islandY + 1, cz - 4, Material.DEAD_BUSH);
            setBlock(world, cx - 1, islandY + 1, cz + 4, Material.CUT_SANDSTONE);
        } else if (type.equals("nether")) {
            setBlock(world, cx - 4, islandY + 1, cz + 1, Material.BASALT);
            setBlock(world, cx - 4, islandY + 2, cz + 1, Material.BASALT);
            setBlock(world, cx + 4, islandY + 1, cz - 2, Material.BLACKSTONE);
            setBlock(world, cx - 2, islandY + 1, cz + 2, Material.SHROOMLIGHT);
        } else {
            setBlock(world, cx - 3, islandY + 1, cz + 2, Material.OAK_LOG);
            setBlock(world, cx - 2, islandY + 1, cz + 2, Material.MOSS_BLOCK);
            setBlock(world, cx + 3, islandY + 1, cz - 2, Material.MOSS_CARPET);
        }
    }

    private void placeStarterDecoration(World world, int x, int y, int z, String type, Random random) {
        if (!world.getBlockAt(x, y, z).getType().isAir()) {
            return;
        }
        if (type.equals("desert")) {
            Material[] options = {Material.DEAD_BUSH, Material.SANDSTONE_SLAB, Material.SMOOTH_SANDSTONE_SLAB};
            setBlock(world, x, y, z, options[random.nextInt(options.length)]);
            return;
        }
        if (type.equals("nether")) {
            Material[] options = {Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.CRIMSON_FUNGUS};
            setBlock(world, x, y, z, options[random.nextInt(options.length)]);
            return;
        }
        Material[] options = {Material.SHORT_GRASS, Material.FERN, Material.POPPY, Material.DANDELION, Material.AZURE_BLUET};
        setBlock(world, x, y, z, options[random.nextInt(options.length)]);
    }

    private void placeCactus(World world, int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            setBlock(world, x, y + i, z, Material.CACTUS);
        }
    }

    private void generateFallbackOakTree(World world, int x, int y, int z) {
        for (int i = 0; i < 5; i++) {
            setBlock(world, x, y + i, z, Material.OAK_LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 3) {
                        setBlock(world, x + dx, y + dy, z + dz, Material.OAK_LEAVES);
                    }
                }
            }
        }
        setBlock(world, x, y + 6, z, Material.OAK_LEAVES);
    }

    private void generateFallbackAcaciaTree(World world, int x, int y, int z) {
        for (int i = 0; i < 5; i++) {
            setBlock(world, x, y + i, z, Material.ACACIA_LOG);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                    setBlock(world, x + dx, y + 4, z + dz, Material.ACACIA_LEAVES);
                }
                if (Math.abs(dx) + Math.abs(dz) <= 2) {
                    setBlock(world, x + dx, y + 5, z + dz, Material.ACACIA_LEAVES);
                }
            }
        }
    }

    private void generateRandomUnderIslandStructure(int islandX, int waterHeight, int islandZ, Random random) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int monumentChance = Math.max(0, Math.min(100, config.getInt("starter-island.monument-chance-percent", 50)));
        if (coordinateNoise(islandX, islandZ, 151) * 100.0 < monumentChance) {
            generatePrismarineMonument(islandX, waterHeight, islandZ, random);
        } else {
            generateShipwreckMonument(islandX, waterHeight, islandZ, random);
        }
    }

    private void generateShipwreckMonument(int islandX, int waterHeight, int islandZ, Random random) {
        World world = getAcidWorld();
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int cx = islandX + config.getInt("starter-island.shipwreck-offset-x", 0);
        int cz = islandZ + config.getInt("starter-island.shipwreck-offset-z", 0);
        int depth = Math.max(45, config.getInt("starter-island.shipwreck-depth-below-water", 45));
        int cy = Math.max(world.getMinHeight() + 12, waterHeight - depth);

        generateShipwreckHull(world, cx, cy, cz, waterHeight);
        generateShipwreckMast(world, cx, cy, cz);
        generateShipwreckCabin(world, cx, cy, cz);
        decorateShipwreck(world, cx, cy, cz, waterHeight, random);

        Location chestLoc = new Location(world, cx - 4, cy + 4, cz + 2);
        Block chestBlock = world.getBlockAt(chestLoc);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();

            ItemStack potion = new ItemStack(Material.POTION);
            PotionMeta meta = (PotionMeta) potion.getItemMeta();
            if (meta != null) {
                meta.addCustomEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 60 * 8, 0), true);
                meta.setDisplayName("§bWater Breathing Potion (Acid Immunity)");
                potion.setItemMeta(meta);
            }
            inv.addItem(potion);

            inv.addItem(new ItemStack(Material.GOLD_NUGGET, 8));
            inv.addItem(new ItemStack(Material.PRISMARINE_SHARD, 4));
            inv.addItem(new ItemStack(Material.PRISMARINE_CRYSTALS, 4));

            if (random.nextBoolean()) {
                inv.addItem(new ItemStack(Material.DIAMOND, 1));
            } else {
                inv.addItem(new ItemStack(Material.GOLD_INGOT, 2));
            }
        }

        for (int i = 0; i < 24; i++) {
            int x = cx - 15 + random.nextInt(31);
            int z = cz - 8 + random.nextInt(17);
            int y = cy + random.nextInt(4);
            Material wreckage = random.nextBoolean() ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
            if (random.nextDouble() < 0.72) {
                setBlock(world, x, y, z, wreckage);
            } else {
                placeSeaPickle(world, x, Math.min(waterHeight, y + 1), z, 1 + random.nextInt(4));
            }
        }
    }

    private void generatePrismarineMonument(int islandX, int waterHeight, int islandZ, Random random) {
        World world = getAcidWorld();
        FileConfiguration config = plugin.getConfigManager().getConfig();
        int depth = Math.max(60, config.getInt("starter-island.monument-depth-below-water", 60));
        int cy = Math.max(world.getMinHeight() + 10, waterHeight - depth);

        generateMonumentBase(world, islandX, cy, islandZ);
        generateMonumentPillars(world, islandX, cy, islandZ);
        generateMonumentSanctum(world, islandX, cy, islandZ);
        decorateMonument(world, islandX, cy, islandZ, waterHeight, random);
        fillMonumentChest(world, islandX, cy, islandZ, random);
    }

    private void generateMonumentBase(World world, int cx, int cy, int cz) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double shape = (dx * dx) / 64.0 + (dz * dz) / 49.0;
                if (shape > 1.0) {
                    continue;
                }
                boolean edge = shape > 0.72;
                Material floor = edge ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS;
                setBlock(world, cx + dx, cy, cz + dz, floor);
                if (edge && coordinateNoise(cx + dx, cz + dz, 173) > 0.18) {
                    setBlock(world, cx + dx, cy + 1, cz + dz, Material.PRISMARINE);
                    if (coordinateNoise(cx + dx, cz + dz, 179) > 0.72) {
                        setBlock(world, cx + dx, cy + 2, cz + dz, Material.SEA_LANTERN);
                    }
                }
            }
        }

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 5) {
                    setBlock(world, cx + dx, cy + 1, cz + dz, Material.PRISMARINE);
                }
            }
        }
    }

    private void generateMonumentPillars(World world, int cx, int cy, int cz) {
        int[][] pillars = {
                {-6, -4}, {-6, 4}, {6, -4}, {6, 4},
                {-3, -6}, {3, -6}, {-3, 6}, {3, 6}
        };
        for (int[] pillar : pillars) {
            int px = cx + pillar[0];
            int pz = cz + pillar[1];
            for (int y = cy + 1; y <= cy + 8; y++) {
                Material material = y == cy + 4 ? Material.SEA_LANTERN : Material.PRISMARINE_BRICKS;
                setBlock(world, px, y, pz, material);
            }
            setBlock(world, px, cy + 9, pz, Material.DARK_PRISMARINE);
            setBlock(world, px, cy + 10, pz, Material.SEA_LANTERN);
        }
    }

    private void generateMonumentSanctum(World world, int cx, int cy, int cz) {
        for (int y = cy + 2; y <= cy + 7; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    boolean doorway = (Math.abs(dx) <= 1 && Math.abs(dz) == 3 && y <= cy + 4)
                            || (Math.abs(dz) <= 1 && Math.abs(dx) == 3 && y <= cy + 4);
                    if (wall && !doorway) {
                        Material material = y == cy + 7 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS;
                        setBlock(world, cx + dx, y, cz + dz, material);
                    }
                }
            }
        }

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 6) {
                    setBlock(world, cx + dx, cy + 8, cz + dz, Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                }
            }
        }
        setBlock(world, cx, cy + 9, cz, Material.PRISMARINE_BRICKS);
        setBlock(world, cx, cy + 10, cz, Material.SEA_LANTERN);
    }

    private void decorateMonument(World world, int cx, int cy, int cz, int waterHeight, Random random) {
        for (int i = 0; i < 34; i++) {
            int x = cx - 10 + random.nextInt(21);
            int z = cz - 9 + random.nextInt(19);
            int y = cy + random.nextInt(5);
            if (random.nextDouble() < 0.35) {
                setBlock(world, x, y, z, random.nextBoolean() ? Material.PRISMARINE : Material.PRISMARINE_BRICKS);
            } else if (random.nextDouble() < 0.6) {
                placeSeaPickle(world, x, Math.min(waterHeight, y + 1), z, 1 + random.nextInt(4));
            } else {
                setBlock(world, x, y, z, Material.SEA_LANTERN);
            }
        }
    }

    private void fillMonumentChest(World world, int cx, int cy, int cz, Random random) {
        Block chestBlock = world.getBlockAt(cx, cy + 3, cz);
        chestBlock.setType(Material.CHEST, false);
        if (!(chestBlock.getState() instanceof Chest chest)) {
            return;
        }

        Inventory inv = chest.getInventory();
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.addCustomEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 60 * 8, 0), true);
            meta.setDisplayName("§bWater Breathing Potion (Acid Immunity)");
            potion.setItemMeta(meta);
        }
        inv.addItem(potion);
        inv.addItem(new ItemStack(Material.PRISMARINE_SHARD, 10));
        inv.addItem(new ItemStack(Material.PRISMARINE_CRYSTALS, 8));
        inv.addItem(new ItemStack(Material.SEA_LANTERN, 2));
        inv.addItem(new ItemStack(Material.GOLD_INGOT, 2 + random.nextInt(3)));
        if (random.nextDouble() < 0.35) {
            inv.addItem(new ItemStack(Material.HEART_OF_THE_SEA, 1));
        }
    }

    private void generateShipwreckHull(World world, int cx, int cy, int cz, int waterHeight) {
        for (int dx = -14; dx <= 14; dx++) {
            double taper = 1.0 - Math.abs(dx) / 15.0;
            int halfWidth = Math.max(1, (int) Math.round(2 + taper * 4));
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                boolean side = Math.abs(dz) == halfWidth;
                boolean innerSide = Math.abs(dz) == halfWidth - 1;
                boolean broken = coordinateNoise(cx + dx, cz + dz, 121) < 0.09 && Math.abs(dx) > 3;
                if (broken) {
                    continue;
                }

                setBlock(world, cx + dx, cy, cz + dz, side ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                if (side || innerSide) {
                    setBlock(world, cx + dx, cy + 1, cz + dz, side ? Material.DARK_OAK_LOG : Material.SPRUCE_PLANKS);
                    if (Math.abs(dx) < 11 && side) {
                        setBlock(world, cx + dx, cy + 2, cz + dz, Material.DARK_OAK_PLANKS);
                    }
                } else if (Math.abs(dx) < 11 && coordinateNoise(cx + dx, cz + dz, 123) > 0.2) {
                    setBlock(world, cx + dx, cy + 2, cz + dz, Material.SPRUCE_SLAB);
                }
            }
        }

        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 1; dy <= 5; dy++) {
                setBlock(world, cx - 15, cy + dy, cz + dz, dy > 3 ? Material.DARK_OAK_FENCE : Material.DARK_OAK_PLANKS);
                setBlock(world, cx + 15, cy + dy, cz + dz, dy > 3 ? Material.SPRUCE_FENCE : Material.SPRUCE_PLANKS);
            }
        }

        carveShipwreckBreach(world, cx - 7, cy + 1, cz - 3, waterHeight);
        carveShipwreckBreach(world, cx + 6, cy + 1, cz + 4, waterHeight);
    }

    private void carveShipwreckBreach(World world, int x, int y, int z, int waterHeight) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    restoreFluidOrAir(world, x + dx, y + dy, z + dz, waterHeight);
                }
            }
        }
    }

    private void generateShipwreckMast(World world, int cx, int cy, int cz) {
        for (int y = cy + 2; y <= cy + 14; y++) {
            setBlock(world, cx, y, cz, Material.SPRUCE_LOG);
        }
        for (int dz = -6; dz <= 6; dz++) {
            setBlock(world, cx, cy + 11, cz + dz, Math.abs(dz) == 6 ? Material.SPRUCE_FENCE : Material.SPRUCE_LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            setBlock(world, cx + dx, cy + 8, cz, Material.DARK_OAK_FENCE);
        }
        for (int dz = -4; dz <= 4; dz++) {
            if (Math.abs(dz) <= 1) {
                continue;
            }
            setBlock(world, cx + 1, cy + 10, cz + dz, Material.WHITE_WOOL);
            if (Math.abs(dz) <= 3) {
                setBlock(world, cx + 1, cy + 9, cz + dz, Material.WHITE_WOOL);
            }
        }
        setBlock(world, cx, cy + 15, cz, Material.LANTERN);
    }

    private void generateShipwreckCabin(World world, int cx, int cy, int cz) {
        for (int dx = 7; dx <= 11; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean wall = dx == 7 || dx == 11 || Math.abs(dz) == 3;
                if (wall) {
                    setBlock(world, cx + dx, cy + 3, cz + dz, Material.STRIPPED_SPRUCE_LOG);
                    setBlock(world, cx + dx, cy + 4, cz + dz, Material.SPRUCE_PLANKS);
                } else {
                    setBlock(world, cx + dx, cy + 3, cz + dz, Material.SPRUCE_SLAB);
                }
            }
        }
        for (int dx = 6; dx <= 12; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dz) <= 4 && dx >= 7 && dx <= 11) {
                    setBlock(world, cx + dx, cy + 5, cz + dz, Material.DARK_OAK_SLAB);
                }
            }
        }
        setBlock(world, cx + 9, cy + 4, cz - 3, Material.SEA_LANTERN);
        setBlock(world, cx + 9, cy + 4, cz + 3, Material.SEA_LANTERN);
    }

    private void decorateShipwreck(World world, int cx, int cy, int cz, int waterHeight, Random random) {
        setBlock(world, cx - 11, cy + 3, cz - 2, Material.BARREL);
        setBlock(world, cx - 10, cy + 3, cz - 1, Material.BARREL);
        setBlock(world, cx + 4, cy + 3, cz + 4, Material.SEA_LANTERN);
        setBlock(world, cx - 5, cy + 3, cz - 5, Material.SEA_LANTERN);
        setBlock(world, cx + 13, cy + 4, cz, Material.IRON_BARS);
        setBlock(world, cx + 13, cy + 3, cz, Material.LANTERN);

        for (int i = 0; i < 18; i++) {
            int x = cx - 13 + random.nextInt(27);
            int z = cz - 6 + random.nextInt(13);
            int y = cy + 2 + random.nextInt(3);
            if (random.nextDouble() < 0.5) {
                setBlock(world, x, y, z, random.nextBoolean() ? Material.SPRUCE_FENCE : Material.DARK_OAK_FENCE);
            } else {
                placeSeaPickle(world, x, Math.min(waterHeight, y), z, 1 + random.nextInt(4));
            }
        }
    }

    private void restoreFluidOrAir(World world, int x, int y, int z, int waterHeight) {
        setBlock(world, x, y, z, y <= waterHeight ? Material.WATER : Material.AIR);
    }

    private void placeSeaPickle(World world, int x, int y, int z, int count) {
        Block block = world.getBlockAt(x, y, z);
        if (!block.getType().isAir() && block.getType() != Material.WATER) {
            return;
        }
        block.setType(Material.SEA_PICKLE, false);
        BlockData data = block.getBlockData();
        if (data instanceof SeaPickle seaPickle) {
            seaPickle.setPickles(Math.max(1, Math.min(4, count)));
        }
        if (data instanceof Waterlogged waterlogged) {
            waterlogged.setWaterlogged(true);
        }
        block.setBlockData(data, false);
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

        cleanupTasks.removeIf(BukkitTask::isCancelled);
        BukkitRunnable cleanupTask = new BukkitRunnable() {
            private int x = minX;
            private int z = minZ;
            private int waitingChunkX = Integer.MIN_VALUE;
            private int waitingChunkZ = Integer.MIN_VALUE;
            private boolean waitingForChunk = false;
            private volatile boolean chunkLoadFailed = false;
            private volatile String chunkLoadFailureMessage = "";

            @Override
            public void run() {
                if (shouldStopWorldTask()) {
                    finishCleanup(false);
                    return;
                }
                if (chunkLoadFailed) {
                    if (!shuttingDown) {
                        plugin.getLogger().warning("Stopping island cleanup at " + island.getX() + ", " + island.getZ()
                                + " because a chunk could not be loaded: " + chunkLoadFailureMessage);
                    }
                    finishCleanup(false);
                    return;
                }

                int processed = 0;
                while (processed < columnsPerTick) {
                    if (!ensureChunkReady(world, x, z)) {
                        return;
                    }
                    try {
                        resetColumn(world, x, z, minY, maxY, waterHeight);
                    } catch (IllegalStateException ex) {
                        if (!shuttingDown) {
                            plugin.getLogger().warning("Stopping island cleanup at " + island.getX() + ", " + island.getZ()
                                    + " because the chunk system rejected a request: " + ex.getMessage());
                        }
                        finishCleanup(false);
                        return;
                    }
                    processed++;

                    z++;
                    if (z > maxZ) {
                        z = minZ;
                        x++;
                    }
                    if (x > maxX) {
                        finishCleanup(true);
                        plugin.getLogger().info("Finished cleaning island at " + island.getX() + ", " + island.getZ() + ".");
                        return;
                    }
                }
            }

            private boolean ensureChunkReady(World world, int blockX, int blockZ) {
                int chunkX = blockX >> 4;
                int chunkZ = blockZ >> 4;
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    waitingForChunk = false;
                    return true;
                }
                if (shouldStopWorldTask()) {
                    finishCleanup(false);
                    return false;
                }
                if (waitingForChunk && waitingChunkX == chunkX && waitingChunkZ == chunkZ) {
                    return false;
                }

                waitingForChunk = true;
                waitingChunkX = chunkX;
                waitingChunkZ = chunkZ;
                try {
                    world.getChunkAtAsync(chunkX, chunkZ).whenComplete((chunk, throwable) -> {
                        waitingForChunk = false;
                        if (throwable != null) {
                            chunkLoadFailed = true;
                            chunkLoadFailureMessage = throwable.getMessage();
                        }
                    });
                } catch (IllegalStateException ex) {
                    if (!shuttingDown) {
                        chunkLoadFailed = true;
                        chunkLoadFailureMessage = ex.getMessage();
                    } else {
                        finishCleanup(false);
                    }
                }
                return false;
            }

            private void finishCleanup(boolean completed) {
                cancel();
                cleanupTasks.removeIf(BukkitTask::isCancelled);
                if (!completed && !shuttingDown && plugin.isEnabled()) {
                    plugin.getLogger().info("Island cleanup at " + island.getX() + ", " + island.getZ() + " was cancelled safely.");
                }
            }
        };
        cleanupTasks.add(cleanupTask.runTaskTimer(plugin, 1L, 1L));
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

        biomeTasks.removeIf(BukkitTask::isCancelled);
        BukkitRunnable biomeTask = new BukkitRunnable() {
            private int x = minX;
            private int z = minZ;

            @Override
            public void run() {
                if (shouldStopWorldTask()) {
                    cancel();
                    biomeTasks.removeIf(BukkitTask::isCancelled);
                    return;
                }
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
                        biomeTasks.removeIf(BukkitTask::isCancelled);
                        return;
                    }
                }
            }
        };
        biomeTasks.add(biomeTask.runTaskTimer(plugin, 1L, 1L));

        island.setTheme(themeId);
        island.invalidateLevelCache();
        plugin.getIslandManager().saveData();
        return true;
    }

    public boolean isThemeValid(String themeId) {
        return getThemeBiome(themeId) != null;
    }

    private boolean isStarterIslandColumn(int worldX, int worldZ, int dx, int dz) {
        boolean core = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
        boolean chestShelf = dx >= 2 && dx <= 3 && dz >= 1 && dz <= 2;
        boolean leashShelf = dx >= -3 && dx <= -2 && dz >= -2 && dz <= -1;
        double oval = (dx * dx) / 30.25 + ((dz + 0.35) * (dz + 0.35)) / 20.25;
        boolean cove = dx >= 4 && dz >= 1 && dz <= 3;
        boolean beachTail = dx <= -4 && Math.abs(dz) <= 1;
        boolean southPoint = dz >= 4 && Math.abs(dx + 1) <= 1;
        boolean northBump = dz <= -4 && dx >= -1 && dx <= 2;
        boolean candidate = oval <= 1.0 || beachTail || southPoint || northBump;
        double edgeBreak = coordinateNoise(worldX, worldZ, 11);
        boolean protectedCenter = core || chestShelf || leashShelf;
        boolean naturalEdge = edgeBreak > 0.2 || oval < 0.68 || core;
        return protectedCenter || (candidate && !cove && naturalEdge);
    }

    private int starterColumnDepth(int dx, int dz) {
        int centerWeight = Math.max(0, 5 - (Math.abs(dx) + Math.abs(dz)));
        int shore = Math.abs(dx) >= 4 || Math.abs(dz) >= 4 ? 0 : 1;
        return 2 + Math.min(3, centerWeight / 2 + shore);
    }

    private Material chooseSurfaceMaterial(int x, int z, StarterPalette palette) {
        double noise = coordinateNoise(x, z, 23);
        if (noise > 0.84) {
            return palette.surfaceAccent();
        }
        if (noise < 0.08) {
            return palette.surfaceDetail();
        }
        return palette.surface();
    }

    private double coordinateNoise(int x, int z, int salt) {
        long value = x * 341873128712L + z * 132897987541L + salt * 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }

    private void setBlock(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    private boolean shouldStopWorldTask() {
        return shuttingDown || !plugin.isEnabled();
    }

    private void cancelScheduledWorldTasks() {
        cleanupTasks.forEach(BukkitTask::cancel);
        cleanupTasks.clear();
        biomeTasks.forEach(BukkitTask::cancel);
        biomeTasks.clear();
    }

    private void resetColumn(World world, int x, int z, int minY, int maxY, int waterHeight) {
        for (int y = minY; y <= maxY; y++) {
            Material target;
            if (y <= waterHeight) {
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

    private record StarterPalette(
            Material surface,
            Material surfaceAccent,
            Material surfaceDetail,
            Material subsurface,
            Material underside,
            Material fence,
            TreeType treeType,
            boolean nether
    ) {
        private static StarterPalette from(String type) {
            return switch (type) {
                case "desert" -> new StarterPalette(
                        Material.SAND,
                        Material.SANDSTONE,
                        Material.SMOOTH_SANDSTONE,
                        Material.SANDSTONE,
                        Material.CUT_SANDSTONE,
                        Material.ACACIA_FENCE,
                        TreeType.ACACIA,
                        false
                );
                case "nether" -> new StarterPalette(
                        Material.NETHERRACK,
                        Material.CRIMSON_NYLIUM,
                        Material.BLACKSTONE,
                        Material.NETHERRACK,
                        Material.BASALT,
                        Material.NETHER_BRICK_FENCE,
                        TreeType.CRIMSON_FUNGUS,
                        true
                );
                default -> new StarterPalette(
                        Material.GRASS_BLOCK,
                        Material.COARSE_DIRT,
                        Material.MOSS_BLOCK,
                        Material.DIRT,
                        Material.ROOTED_DIRT,
                        Material.OAK_FENCE,
                        TreeType.TREE,
                        false
                );
            };
        }
    }
}
