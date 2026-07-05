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
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
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
                rawMessage = rawMessage.replace(placeholders[i], placeholders[i + 1] == null ? "" : placeholders[i + 1]);
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
