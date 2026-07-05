package id.alvarennation.acidIsland;

import id.alvarennation.acidIsland.commands.AcidIslandCommand;
import id.alvarennation.acidIsland.commands.AcidIslandTabCompleter;
import id.alvarennation.acidIsland.config.ConfigManager;
import id.alvarennation.acidIsland.gui.IslandGUI;
import id.alvarennation.acidIsland.hooks.ConverseCraftHook;
import id.alvarennation.acidIsland.hooks.FloodgateHook;
import id.alvarennation.acidIsland.hooks.VaultHook;
import id.alvarennation.acidIsland.island.IslandManager;
import id.alvarennation.acidIsland.listeners.AcidListener;
import id.alvarennation.acidIsland.listeners.GeneratorListener;
import id.alvarennation.acidIsland.listeners.IslandProtectionListener;
import id.alvarennation.acidIsland.quest.QuestManager;
import id.alvarennation.acidIsland.world.VoidWorldGenerator;
import id.alvarennation.acidIsland.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import org.jetbrains.annotations.Nullable;

public final class AcidIsland extends JavaPlugin {

    private ConfigManager configManager;
    private WorldManager worldManager;
    private IslandManager islandManager;
    private IslandGUI islandGUI;
    private QuestManager questManager;
    private ConverseCraftHook converseCraftHook;
    private GeneratorListener generatorListener;

    @Override
    public void onEnable() {
        // 1. Setup Config
        this.configManager = new ConfigManager(this);

        // 2. Setup Hooks
        FloodgateHook.setup();
        id.alvarennation.acidIsland.hooks.MMOItemsHook.setup();
        id.alvarennation.acidIsland.hooks.SkullsHook.setup();
        id.alvarennation.acidIsland.hooks.AlvarenHook.setup(getLogger());
        id.alvarennation.acidIsland.hooks.ModelEngineHook.setup(getLogger());
        id.alvarennation.acidIsland.hooks.PlaceholderHook.setup(this);
        if (VaultHook.setupEconomy()) {
            getLogger().info("Successfully hooked into Vault Economy.");
        } else {
            getLogger().warning("Vault Economy not found. Upgrades and Bank will not work!");
        }

        // 3. Setup World and Island Managers
        this.worldManager = new WorldManager(this);
        this.worldManager.initWorld(); // Load/Create toxic ocean world

        this.islandManager = new IslandManager(this);
        this.questManager = new QuestManager(this);
        this.islandGUI = new IslandGUI(this);
        this.converseCraftHook = new ConverseCraftHook(this);
        this.converseCraftHook.setup();

        // 4. Register Listeners
        Bukkit.getPluginManager().registerEvents(new AcidListener(this), this);
        Bukkit.getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        this.generatorListener = new GeneratorListener(this);
        Bukkit.getPluginManager().registerEvents(generatorListener, this);
        Bukkit.getPluginManager().registerEvents(islandGUI, this);

        // 6. Register Commands
        if (getCommand("ai") != null) {
            getCommand("ai").setExecutor(new AcidIslandCommand(this));
            getCommand("ai").setTabCompleter(new AcidIslandTabCompleter(this));
        } else {
            getLogger().severe("Command /ai is not registered. Check plugin.yml commands section.");
        }

        getLogger().info("AcidIsland Plugin has been enabled!");
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        int waterHeight = getConfig().getInt("acid-water.height", 62);
        return new VoidWorldGenerator(waterHeight);
    }

    @Override
    public void onDisable() {
        if (islandGUI != null) {
            islandGUI.flushAllVaults();
        }
        if (worldManager != null) {
            worldManager.cancelIslandValueScans();
        }
        // Save all data on plugin shutdown
        if (islandManager != null) {
            islandManager.saveData();
            getLogger().info("AcidIsland data saved successfully.");
        }
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("AcidIsland Plugin has been disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public IslandGUI getIslandGUI() {
        return islandGUI;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public GeneratorListener getGeneratorListener() {
        return generatorListener;
    }

    public ConverseCraftHook getConverseCraftHook() {
        return converseCraftHook;
    }

    public Location getLobbyLocation() {
        String worldName = getConfigManager().getConfig().getString("lobby.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(
                world,
                getConfigManager().getConfig().getDouble("lobby.x", world.getSpawnLocation().getX()),
                getConfigManager().getConfig().getDouble("lobby.y", world.getSpawnLocation().getY()),
                getConfigManager().getConfig().getDouble("lobby.z", world.getSpawnLocation().getZ()),
                (float) getConfigManager().getConfig().getDouble("lobby.yaw", 0.0),
                (float) getConfigManager().getConfig().getDouble("lobby.pitch", 0.0)
        );
    }

    public void setLobbyLocation(Location location) {
        getConfigManager().getConfig().set("lobby.world", location.getWorld().getName());
        getConfigManager().getConfig().set("lobby.x", location.getX());
        getConfigManager().getConfig().set("lobby.y", location.getY());
        getConfigManager().getConfig().set("lobby.z", location.getZ());
        getConfigManager().getConfig().set("lobby.yaw", location.getYaw());
        getConfigManager().getConfig().set("lobby.pitch", location.getPitch());
        saveConfig();
    }
}
