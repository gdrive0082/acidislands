package id.alvarennation.acidIsland.config;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.hooks.FloodgateHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final AcidIsland plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(AcidIsland plugin) {
        this.plugin = plugin;
        setupFiles();
    }

    public void setupFiles() {
        // Save default config
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        migrateConfig();

        // Save and load messages.yml
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        migrateConfig();
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public String getAcidWorldName() {
        String legacy = config.getString("world-name", "acid_island_world");
        String configured = config.getString("acid-world", legacy);
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("world")) {
            return "acid_island_world";
        }
        return configured;
    }

    public long getCreateCooldownSeconds() {
        return getCooldownSeconds("cooldowns.create-seconds");
    }

    public long getDeleteCooldownSeconds() {
        return getCooldownSeconds("cooldowns.delete-seconds");
    }

    private long getCooldownSeconds(String path) {
        return Math.max(0L, config.getLong(path, 120L));
    }

    private void migrateConfig() {
        int previousVersion = config.getInt("config-version", 0);
        int bundledVersion = 15;
        try (InputStream defaultsStream = plugin.getResource("config.yml")) {
            if (defaultsStream != null) {
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
                );
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                bundledVersion = defaults.getInt("config-version", bundledVersion);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load bundled config defaults: " + ex.getMessage());
        }

        migrateCooldown("cooldowns.create-seconds", previousVersion);
        migrateCooldown("cooldowns.delete-seconds", previousVersion);
        migrateStarterIslandLayout(previousVersion);
        migrateStarterBorder(previousVersion);
        if (previousVersion < bundledVersion) {
            config.set("config-version", bundledVersion);
        }
        plugin.saveConfig();
    }

    private void migrateCooldown(String path, int previousVersion) {
        long value = config.getLong(path, -1L);
        if (value < 0L || (previousVersion < 10 && value >= 1800L)) {
            config.set(path, 120L);
        }
    }

    private void migrateStarterIslandLayout(int previousVersion) {
        if (previousVersion >= 14) {
            return;
        }
        migrateIntWhenDefault("starter-island.surface-offset-above-water", 2, 0);
        migrateIntWhenDefault("starter-island.shipwreck-offset-x", 20, 0);
        migrateIntWhenDefault("starter-island.shipwreck-offset-z", 17, 0);
        migrateIntWhenDefault("starter-island.shipwreck-depth-below-water", 5, 22);
    }

    private void migrateStarterBorder(int previousVersion) {
        if (previousVersion >= 15) {
            return;
        }
        migrateIntWhenDefault("upgrades.border.1.size", 50, 15);
    }

    private void migrateIntWhenDefault(String path, int oldDefault, int newDefault) {
        if (!config.contains(path) || config.getInt(path) == oldDefault) {
            config.set(path, newDefault);
        }
    }

    /**
     * Get translated Component message for a player, prepended with Java/Bedrock prefix.
     */
    public Component getMessage(Player player, String path) {
        String rawMessage = messages.getString("messages." + path, "&cMessage path not found: " + path);
        return formatMessage(player, rawMessage);
    }

    /**
     * Get translated Component message with placeholders.
     */
    public Component getMessage(Player player, String path, String... placeholders) {
        String rawMessage = messages.getString("messages." + path, "&cMessage path not found: " + path);
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String key = placeholders[i];
                String value = placeholders[i + 1] == null ? "" : placeholders[i + 1];
                rawMessage = rawMessage.replace(key, value);
                if (key.startsWith("{") && key.endsWith("}")) {
                    rawMessage = rawMessage.replace("$" + key, value);
                }
            }
        }
        return formatMessage(player, rawMessage);
    }

    /**
     * Format a raw string with Java/Bedrock prefix and legacy colors.
     */
    public Component formatMessage(Player player, String rawText) {
        String prefix = getPrefix(player);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + rawText);
    }

    /**
     * Format legacy colors directly.
     */
    public Component format(String rawText) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(rawText);
    }

    /**
     * Translate legacy text to string for item display names/lores.
     */
    public String colorize(String text) {
        // Just translate legacy codes using standard ChatColor or just return legacy format for GUI items
        return text.replace('&', '§');
    }

    private String getPrefix(Player player) {
        if (player == null) return messages.getString("prefixes.java", "");
        boolean isBedrock = FloodgateHook.isBedrockPlayer(player.getUniqueId());
        return isBedrock ? messages.getString("prefixes.bedrock", "") : messages.getString("prefixes.java", "");
    }
}
