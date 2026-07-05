package id.alvarennation.acidIsland;

import id.alvarennation.acidIsland.commands.AcidIslandCommand;
import id.alvarennation.acidIsland.commands.AcidIslandTabCompleter;
import id.alvarennation.acidIsland.config.ConfigManager;
import id.alvarennation.acidIsland.gui.IslandGUI;
import id.alvarennation.acidIsland.hooks.FloodgateHook;
import id.alvarennation.acidIsland.hooks.VaultHook;
import id.alvarennation.acidIsland.island.IslandManager;
import id.alvarennation.acidIsland.listeners.AcidListener;
import id.alvarennation.acidIsland.listeners.GeneratorListener;
import id.alvarennation.acidIsland.listeners.IslandProtectionListener;
import id.alvarennation.acidIsland.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class AcidIsland extends JavaPlugin {

    private ConfigManager configManager;
    private WorldManager worldManager;
    private IslandManager islandManager;
    private IslandGUI islandGUI;
    private Location lobbyLocation;

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
        this.islandGUI = new IslandGUI(this);

        // 4. Register Listeners
        Bukkit.getPluginManager().registerEvents(new AcidListener(this), this);
        Bukkit.getPluginManager().registerEvents(new IslandProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GeneratorListener(this), this);
        Bukkit.getPluginManager().registerEvents(islandGUI, this);

        // 6. Register Commands
        if (getCommand("ai") != null) {
            getCommand("ai").setExecutor(new AcidIslandCommand(this));
            getCommand("ai").setTabCompleter( new AcidIslandTabCompleter(this));
        }

        getLogger().info("AcidIsland Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save all data on plugin shutdown
        if (islandManager != null) {
            islandManager.saveData();
            getLogger().info("AcidIsland data saved successfully.");
        }
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

    public Location getLobbyLocation() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }
}
