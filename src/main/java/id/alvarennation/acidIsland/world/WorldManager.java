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
        int waterHeight = plugin.getConfigManager().getConfig().getInt("acid-water.height", 62);
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

        generateMiniMonument(cx, Math.max(world.getMinHeight() + 12, waterHeight - 22), cz);
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
        for (int x = cx - 9; x <= cx + 9; x++) {
            for (int z = cz - 9; z <= cz + 9; z++) {
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
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                if (!isStarterIslandColumn(cx + dx, cz + dz, dx, dz)) {
                    continue;
                }

                double normalized = normalizedIslandDistance(dx, dz);
                int depth = 2 + Math.max(0, (int) Math.round((1.0 - normalized) * 4.0));
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

        setBlock(world, cx, waterHeight - 1, cz, Material.BEDROCK);
    }

    private void generateNaturalSupports(World world, int cx, int islandY, int cz, int waterHeight, StarterPalette palette) {
        generateSupport(world, cx, islandY, cz, waterHeight, palette, 0, 0, 3);
        generateSupport(world, cx, islandY, cz, waterHeight + 3, palette, -3, 2, 2);
        generateSupport(world, cx, islandY, cz, waterHeight + 4, palette, 3, -2, 2);
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

        for (int i = 0; i < 18; i++) {
            int dx = random.nextInt(15) - 7;
            int dz = random.nextInt(13) - 6;
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
            setBlock(world, cx - 2, islandY + 1, cz + 3, Material.SHROOMLIGHT);
        } else {
            setBlock(world, cx - 4, islandY + 1, cz + 2, Material.OAK_LOG);
            setBlock(world, cx - 3, islandY + 1, cz + 3, Material.MOSS_BLOCK);
            setBlock(world, cx + 4, islandY + 1, cz - 2, Material.MOSS_CARPET);
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

    private void generateMiniMonument(int cx, int cy, int cz) {
        World world = getAcidWorld();
        Random random = new Random((((long) cx) << 32) ^ cz ^ 0x5A17BEEF);

        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= 5.4) {
                    Material floor = distance <= 2.2 ? Material.DARK_PRISMARINE : chooseRuinBlock(cx + dx, cz + dz);
                    setBlock(world, cx + dx, cy - 1, cz + dz, Material.PRISMARINE);
                    setBlock(world, cx + dx, cy, cz + dz, floor);
                }
            }
        }

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                boolean doorway = (Math.abs(dx) <= 1 && Math.abs(dz) >= 4) || (Math.abs(dz) <= 1 && Math.abs(dx) >= 4);
                if (distance < 4.2 || distance > 5.5 || doorway) {
                    continue;
                }
                int height = coordinateNoise(cx + dx, cz + dz, 91) > 0.36 ? 2 : 1;
                for (int y = 1; y <= height; y++) {
                    setBlock(world, cx + dx, cy + y, cz + dz, y == height ? Material.PRISMARINE_BRICKS : Material.PRISMARINE);
                }
            }
        }

        int[][] pillars = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}};
        for (int[] pillar : pillars) {
            for (int y = 1; y <= 5; y++) {
                Material block = y % 2 == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS;
                setBlock(world, cx + pillar[0], cy + y, cz + pillar[1], block);
            }
            setBlock(world, cx + pillar[0], cy + 6, cz + pillar[1], Material.SEA_LANTERN);
        }

        for (int i = -2; i <= 2; i++) {
            setBlock(world, cx + i, cy + 4, cz + 5, Material.PRISMARINE_BRICKS);
            setBlock(world, cx + i, cy + 4, cz - 5, Material.PRISMARINE_BRICKS);
            setBlock(world, cx + 5, cy + 4, cz + i, Material.PRISMARINE_BRICKS);
            setBlock(world, cx - 5, cy + 4, cz + i, Material.PRISMARINE_BRICKS);
        }

        setBlock(world, cx, cy + 1, cz, Material.DARK_PRISMARINE);
        setBlock(world, cx + 1, cy + 1, cz, Material.SEA_LANTERN);
        setBlock(world, cx - 1, cy + 1, cz, Material.SEA_LANTERN);
        Location chestLoc = new Location(world, cx, cy + 2, cz);
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

        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int radius = 6 + random.nextInt(4);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int z = cz + (int) Math.round(Math.sin(angle) * radius);
            setBlock(world, x, cy - 1 + random.nextInt(2), z, chooseRuinBlock(x, z));
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

    private boolean isStarterIslandColumn(int worldX, int worldZ, int dx, int dz) {
        double normalized = normalizedIslandDistance(dx, dz);
        double wobble = (coordinateNoise(worldX, worldZ, 11) - 0.5) * 0.28;
        boolean protectedCenter = Math.abs(dx) <= 3 && Math.abs(dz) <= 3;
        return protectedCenter || normalized + wobble <= 1.0;
    }

    private double normalizedIslandDistance(int dx, int dz) {
        return (dx * dx) / 56.25 + (dz * dz) / 36.0;
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

    private Material chooseRuinBlock(int x, int z) {
        double noise = coordinateNoise(x, z, 67);
        if (noise > 0.82) {
            return Material.DARK_PRISMARINE;
        }
        if (noise > 0.52) {
            return Material.PRISMARINE_BRICKS;
        }
        return Material.PRISMARINE;
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
