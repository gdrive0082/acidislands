package id.alvarennation.acidIsland.listeners;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GeneratorListener implements Listener {

    private final AcidIsland plugin;
    private final Random random = new Random();

    public GeneratorListener(AcidIsland plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Material formedType = event.getNewState().getType();
        
        // Cobblestone generator menghasilkan Cobblestone atau Stone (dan Basalt untuk nether)
        if (formedType == Material.COBBLESTONE || formedType == Material.STONE || formedType == Material.BASALT) {
            Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
            if (island == null) return;

            int genLevel = island.getLevel("generator");
            Material selectedOre = getRandomOre(genLevel);

            if (selectedOre != null && selectedOre != formedType) {
                // Mutasi block state yang akan diletakkan
                BlockState state = event.getNewState();
                state.setType(selectedOre);
            }
        }
    }

    private Material getRandomOre(int level) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection section = config.getConfigurationSection("upgrades.generator." + level + ".rates");
        
        if (section == null) {
            return Material.COBBLESTONE;
        }

        Map<Material, Double> rates = new HashMap<>();
        double totalWeight = 0.0;

        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat != null) {
                double weight = section.getDouble(key);
                rates.put(mat, weight);
                totalWeight += weight;
            }
        }

        if (rates.isEmpty()) {
            return Material.COBBLESTONE;
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Map.Entry<Material, Double> entry : rates.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }

        return Material.COBBLESTONE;
    }
}
