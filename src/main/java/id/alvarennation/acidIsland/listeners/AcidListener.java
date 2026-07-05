package id.alvarennation.acidIsland.listeners;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AcidListener implements Listener {

    private final AcidIsland plugin;
    private final Map<UUID, Long> lastWaterDamage = new HashMap<>();
    private final Map<UUID, Long> lastRainDamage = new HashMap<>();

    public AcidListener(AcidIsland plugin) {
        this.plugin = plugin;
        startAcidTask();
    }

    private void startAcidTask() {
        long taskInterval = Math.max(20L, plugin.getConfigManager().getConfig().getLong("optimization.acid-task-interval-ticks", 20L));
        new BukkitRunnable() {
            @Override
            public void run() {
                FileConfiguration config = plugin.getConfigManager().getConfig();
                String worldName = plugin.getConfigManager().getAcidWorldName();
                World world = Bukkit.getWorld(worldName);
                
                if (world == null) return;

                boolean waterEnabled = config.getBoolean("acid-water.enabled", true);
                int waterHeight = config.getInt("acid-water.height", 62);
                double waterDamage = config.getDouble("acid-water.damage-amount", 4.0);
                long waterIntervalMillis = Math.max(1, config.getInt("acid-water.damage-interval", 1)) * 1000L;

                boolean poisonEnabled = config.getBoolean("acid-water.poison-effect.enabled", true);
                int poisonDuration = config.getInt("acid-water.poison-effect.duration-seconds", 3) * 20;
                int poisonAmp = config.getInt("acid-water.poison-effect.amplifier", 1) - 1;

                boolean rainEnabled = config.getBoolean("acid-rain.enabled", true);
                double rainDamage = config.getDouble("acid-rain.damage-amount", 1.0);
                long rainIntervalMillis = Math.max(1, config.getInt("acid-rain.damage-interval", 1)) * 1000L;

                boolean hasStorm = world.hasStorm();
                long now = System.currentTimeMillis();

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
                        if (loc.getY() <= waterHeight + 1) {
                            Material feetType = loc.getBlock().getType();
                            Material headType = player.getEyeLocation().getBlock().getType();
                            
                            if (feetType == Material.WATER || headType == Material.WATER) {
                                long lastDamage = lastWaterDamage.getOrDefault(player.getUniqueId(), 0L);
                                if (now - lastDamage >= waterIntervalMillis) {
                                    player.damage(waterDamage);
                                    lastWaterDamage.put(player.getUniqueId(), now);
                                    if (poisonEnabled) {
                                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, poisonAmp));
                                    }
                                }
                                continue; // Water damage has priority over rain damage.
                            }
                        }
                    }

                    // 3. Toxic Rain Check
                    if (rainEnabled && hasStorm) {
                        Island island = plugin.getIslandManager().getIslandAt(loc);
                        if (island != null && island.getPremiumSetting("weather-lock")) {
                            continue;
                        }
                        // Check if player is exposed to the sky
                        int highestY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
                        if (highestY <= loc.getY()) {
                            long lastDamage = lastRainDamage.getOrDefault(player.getUniqueId(), 0L);
                            if (now - lastDamage >= rainIntervalMillis) {
                                player.damage(rainDamage);
                                lastRainDamage.put(player.getUniqueId(), now);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, taskInterval, taskInterval);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastWaterDamage.remove(uuid);
        lastRainDamage.remove(uuid);
    }
}
