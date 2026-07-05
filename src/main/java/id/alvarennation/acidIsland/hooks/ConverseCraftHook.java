package id.alvarennation.acidIsland.hooks;

import id.alvarennation.acidIsland.AcidIsland;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ConverseCraftHook implements Listener {

    private final AcidIsland plugin;
    private Plugin converseCraftPlugin;
    private boolean enabled;

    private Method endGetPlayer;
    private Method endGetConversationName;
    private Method endGetReason;
    private Method getPlayerDataStore;
    private Method dataStoreSetFlag;
    private Method dataStoreSave;
    private Method getConversationManager;
    private Method conversationStart;

    public ConverseCraftHook(AcidIsland plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (!plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.enabled", true)) {
            return;
        }

        converseCraftPlugin = Bukkit.getPluginManager().getPlugin("ConverseCraft");
        if (converseCraftPlugin == null || !converseCraftPlugin.isEnabled()) {
            plugin.getLogger().info("ConverseCraft not found. Story integration will use commands only.");
            return;
        }

        try {
            Class<?> endEventClass = Class.forName("id.daffa.conversecraft.api.event.ConversationEndEvent");
            endGetPlayer = endEventClass.getMethod("getPlayer");
            endGetConversationName = endEventClass.getMethod("getConversationName");
            endGetReason = endEventClass.getMethod("getReason");

            getPlayerDataStore = converseCraftPlugin.getClass().getMethod("getPlayerDataStore");
            Object dataStore = getPlayerDataStore.invoke(converseCraftPlugin);
            dataStoreSetFlag = dataStore.getClass().getMethod("setFlag", UUID.class, String.class, boolean.class);
            dataStoreSave = dataStore.getClass().getMethod("save");

            getConversationManager = converseCraftPlugin.getClass().getMethod("getConversationManager");
            Object manager = getConversationManager.invoke(converseCraftPlugin);
            conversationStart = manager.getClass().getMethod("start", Player.class, String.class);

            @SuppressWarnings("unchecked")
            Class<? extends Event> typedEndEventClass = (Class<? extends Event>) endEventClass;
            Bukkit.getPluginManager().registerEvent(
                    typedEndEventClass,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> handleConversationEnd(event),
                    plugin,
                    true
            );

            enabled = true;
            syncKnownStoryStages();
            plugin.getLogger().info("Hooked ConverseCraft story integration.");
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook ConverseCraft integration.", ex);
        }
    }

    public boolean isEnabled() {
        return enabled && converseCraftPlugin != null && converseCraftPlugin.isEnabled();
    }

    public boolean startConversation(Player player, String conversationName) {
        if (!isEnabled() || conversationName == null || conversationName.isBlank()) {
            return false;
        }

        try {
            Object manager = getConversationManager.invoke(converseCraftPlugin);
            Object result = conversationStart.invoke(manager, player, conversationName);
            return result instanceof Boolean success && success;
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to start ConverseCraft conversation '" + conversationName + "'.", ex);
            return false;
        }
    }

    public void syncStoryStage(UUID playerUuid, int stage) {
        if (!isEnabled() || !plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.sync-flags-to-conversecraft", true)) {
            return;
        }

        String flagPrefix = plugin.getConfigManager().getConfig().getString("integrations.conversecraft.flag-prefix", "acidisland.story.stage.");
        int maxStage = Math.max(stage, plugin.getConfigManager().getConfig().getInt("integrations.conversecraft.max-stage-flags", 10));
        try {
            Object dataStore = getPlayerDataStore.invoke(converseCraftPlugin);
            for (int i = 1; i <= maxStage; i++) {
                dataStoreSetFlag.invoke(dataStore, playerUuid, flagPrefix + i, i <= stage);
            }
            dataStoreSave.invoke(dataStore);
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to sync story stage flags to ConverseCraft.", ex);
        }
    }

    private void handleConversationEnd(Event event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.sync-stage-from-completed-conversations", true)) {
            return;
        }

        try {
            Object reason = endGetReason.invoke(event);
            if (reason == null || !reason.toString().equalsIgnoreCase("COMPLETE")) {
                return;
            }

            Player player = (Player) endGetPlayer.invoke(event);
            String conversationName = (String) endGetConversationName.invoke(event);
            int mappedStage = getMappedStoryStage(conversationName);
            if (mappedStage <= 0) {
                return;
            }

            int currentStage = plugin.getIslandManager().getStoryStage(player.getUniqueId());
            boolean onlyIncrease = plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.only-increase-stage", true);
            int newStage = onlyIncrease ? Math.max(currentStage, mappedStage) : mappedStage;
            plugin.getIslandManager().setStoryStage(player.getUniqueId(), newStage);

            if (newStage > currentStage && plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.notify-stage-up", true)) {
                player.sendMessage(plugin.getConfigManager().format("&aStory stage AcidIsland naik ke &e" + newStage + "&a."));
            }
        } catch (ReflectiveOperationException | ClassCastException | IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle ConverseCraft conversation completion.", ex);
        }
    }

    public int getMappedStoryStage(String conversationName) {
        if (conversationName == null || conversationName.isBlank()) {
            return 0;
        }

        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("integrations.conversecraft.story-stage-conversations");
        if (section == null) {
            return 0;
        }

        if (section.contains(conversationName)) {
            return section.getInt(conversationName, 0);
        }
        return section.getInt(conversationName.toLowerCase(Locale.ROOT), 0);
    }

    private void syncKnownStoryStages() {
        if (!plugin.getConfigManager().getConfig().getBoolean("integrations.conversecraft.sync-flags-to-conversecraft", true)) {
            return;
        }

        for (Map.Entry<UUID, Integer> entry : plugin.getIslandManager().getStoryStagesSnapshot().entrySet()) {
            syncStoryStage(entry.getKey(), entry.getValue());
        }
    }
}
