package id.alvarennation.acidIsland.listeners;

import id.alvarennation.acidIsland.AcidIsland;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class WorldLifecycleListener implements Listener {

    private final AcidIsland plugin;

    public WorldLifecycleListener(AcidIsland plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }
        String acidWorldName = plugin.getConfigManager().getAcidWorldName();
        if (event.getWorld().getName().equals(acidWorldName)) {
            plugin.getWorldManager().normalizeNewAcidChunk(event.getChunk());
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        String acidWorldName = plugin.getConfigManager().getAcidWorldName();
        if (event.getWorld().getName().equals(acidWorldName)) {
            plugin.getWorldManager().markAcidWorldUnavailable("world unload");
        }
    }
}
