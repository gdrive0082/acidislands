package id.alvarennation.acidIsland.listeners;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class GeneratorListener implements Listener {

    private final AcidIsland plugin;
    private final Map<Integer, OreTable> oreTables = new ConcurrentHashMap<>();

    public GeneratorListener(AcidIsland plugin) {
        this.plugin = plugin;
        rebuildOreTables();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Material formedType = event.getNewState().getType();
        
        // Cobblestone generator menghasilkan Cobblestone atau Stone (dan Basalt untuk nether)
        if (formedType == Material.COBBLESTONE || formedType == Material.STONE || formedType == Material.BASALT) {
            Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
            if (island == null) return;

            int genLevel = island.getLevel("generator");
            Material selectedOre = oreTables.getOrDefault(genLevel, OreTable.DEFAULT).roll();

            if (selectedOre != null && selectedOre != formedType) {
                // Mutasi block state yang akan diletakkan
                BlockState state = event.getNewState();
                state.setType(selectedOre);
            }
        }
    }

    public void rebuildOreTables() {
        Map<Integer, OreTable> rebuilt = new ConcurrentHashMap<>();
        ConfigurationSection generatorSection = plugin.getConfigManager().getConfig().getConfigurationSection("upgrades.generator");
        if (generatorSection != null) {
            for (String key : generatorSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    rebuilt.put(level, OreTable.from(generatorSection.getConfigurationSection(key + ".rates")));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        oreTables.clear();
        oreTables.putAll(rebuilt);
    }

    private record OreTable(Material[] materials, double[] cumulativeWeights, double totalWeight) {
        private static final OreTable DEFAULT = new OreTable(new Material[]{Material.COBBLESTONE}, new double[]{1.0}, 1.0);

        private Material roll() {
            double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
            int index = Arrays.binarySearch(cumulativeWeights, roll);
            if (index < 0) {
                index = -index - 1;
            }
            if (index >= materials.length) {
                index = materials.length - 1;
            }
            return materials[index];
        }

        private static OreTable from(ConfigurationSection section) {
            if (section == null) {
                return DEFAULT;
            }

            Material[] materials = new Material[section.getKeys(false).size()];
            double[] cumulative = new double[materials.length];
            int index = 0;
            double total = 0.0;
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                double weight = section.getDouble(key);
                if (material == null || weight <= 0) {
                    continue;
                }
                total += weight;
                materials[index] = material;
                cumulative[index] = total;
                index++;
            }

            if (index == 0 || total <= 0.0) {
                return DEFAULT;
            }
            return new OreTable(Arrays.copyOf(materials, index), Arrays.copyOf(cumulative, index), total);
        }
    }
}
