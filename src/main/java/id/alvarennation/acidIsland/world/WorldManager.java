package id.alvarennation.acidIsland.world;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

public class WorldManager {

    private final AcidIsland plugin;
    private World acidWorld;

    public WorldManager(AcidIsland plugin) {
        this.plugin = plugin;
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

        if (type.equalsIgnoreCase("desert")) {
            surfaceMat = Material.SAND;
            subMat = Material.SANDSTONE;
            fenceMat = Material.ACACIA_FENCE;
            treeType = TreeType.ACACIA;
        } else if (type.equalsIgnoreCase("nether")) {
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
        Location treeLoc = new Location(world, cx, islandY + 1, cz);
        world.generateTree(treeLoc, treeType);

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
        LeashHitch hitch = world.spawn(fenceLoc, LeashHitch.class);
        sheep.setLeashHolder(hitch);

        // 7. Generate Mini Ocean Monument di bawah air (Y=40)
        generateMiniMonument(cx, 40, cz);
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
}
