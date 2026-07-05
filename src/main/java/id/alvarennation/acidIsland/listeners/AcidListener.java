package id.alvarennation.acidIsland.listeners;

import id.alvarennation.acidIsland.AcidIsland;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class AcidListener implements Listener {

    private final AcidIsland plugin;

    public AcidListener(AcidIsland plugin) {
        this.plugin = plugin;
        startAcidTask();
    }

    private void startAcidTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                FileConfiguration config = plugin.getConfigManager().getConfig();
                String worldName = config.getString("world-name", "acid_island_world");
                World world = Bukkit.getWorld(worldName);
                
                if (world == null) return;

                boolean waterEnabled = config.getBoolean("acid-water.enabled", true);
                int waterHeight = config.getInt("acid-water.height", 62);
                double waterDamage = config.getDouble("acid-water.damage-amount", 4.0);

                boolean poisonEnabled = config.getBoolean("acid-water.poison-effect.enabled", true);
                int poisonDuration = config.getInt("acid-water.poison-effect.duration-seconds", 3) * 20;
                int poisonAmp = config.getInt("acid-water.poison-effect.amplifier", 1) - 1;

                boolean rainEnabled = config.getBoolean("acid-rain.enabled", true);
                double rainDamage = config.getDouble("acid-rain.damage-amount", 1.0);

                boolean hasStorm = world.hasStorm();

                for (Player player : world.getPlayers()) {
                    if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        continue;
                    }

                    // 1. Water Breathing Immunity Check
                    if (player.hasPotionEffect(PotionEffectType.WATER_BREATHING)) {
                        continue;
                    }

                    Location loc = player.getLocation();
                    
                    // 2. Toxic Water Check
                    if (waterEnabled) {
                        BlockFaceCheck:
                        if (loc.getY() <= waterHeight + 1) {
                            Material feetType = loc.getBlock().getType();
                            Material headType = player.getEyeLocation().getBlock().getType();
                            
                            if (feetType == Material.WATER || headType == Material.WATER) {
                                // Apply Water Acid Damage
                                player.damage(waterDamage);
                                if (poisonEnabled) {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, poisonAmp));
                                }
                                break BlockFaceCheck; // Already got water damage, skip rain
                            }
                        }
                    }

                    // 3. Toxic Rain Check
                    if (rainEnabled && hasStorm) {
                        // Check if player is exposed to the sky
                        int highestY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
                        if (highestY <= loc.getY()) {
                            // Player is exposed to rain
                            player.damage(rainDamage);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every 1 second (20 ticks)
    }
}
