package id.alvarennation.acidIsland.listeners;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final AcidIsland plugin;
    private final Set<UUID> pluginFlightPlayers = new HashSet<>();
    private final Map<UUID, Boolean> previousAllowFlight = new HashMap<>();

    public IslandProtectionListener(AcidIsland plugin) {
        this.plugin = plugin;
        startIslandTracker();
    }

    private void startIslandTracker() {
        // Task periodik untuk Fly Mode, Weather Lock, dan Time Lock
        new BukkitRunnable() {
            @Override
            public void run() {
                String acidWorldName = plugin.getConfigManager().getConfig().getString("world-name", "acid_island_world");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!player.getWorld().getName().equals(acidWorldName)) {
                        disablePluginFlight(player);
                        continue;
                    }
                    if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        continue;
                    }

                    Island island = plugin.getIslandManager().getIslandAt(player.getLocation());
                    if (island != null) {
                        boolean isMember = island.isMember(uuid);

                        // 1. Weather Lock
                        if (island.getPremiumSetting("weather-lock")) {
                            player.setPlayerWeather(WeatherType.CLEAR);
                        } else {
                            player.resetPlayerWeather();
                        }

                        // 2. Time Lock (Locked to Noon: 6000)
                        if (island.getPremiumSetting("time-lock")) {
                            player.setPlayerTime(6000L, false);
                        } else {
                            player.resetPlayerTime();
                        }

                        // 3. Fly Mode (Hanya untuk owner/member jika diaktifkan)
                        if (island.getPremiumSetting("fly-mode") && isMember) {
                            enablePluginFlight(player);
                        } else {
                            disablePluginFlight(player);
                        }
                    } else {
                        // Reset when leaving island
                        player.resetPlayerWeather();
                        player.resetPlayerTime();
                        disablePluginFlight(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // Setiap 2 detik
    }

    // ==========================================
    // Basic Settings
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)) return;

        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }

        if (damager == null) return;

        Island island = plugin.getIslandManager().getIslandAt(damaged.getLocation());
        if (island != null) {
            if (!island.getBasicSetting("pvp")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVisitorEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) return;

        Player damager = getResponsiblePlayer(event.getDamager());
        if (damager == null || damager.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getEntity().getLocation());
        if (island == null || island.isMember(damager.getUniqueId())) return;

        event.setCancelled(true);
        damager.sendMessage(plugin.getConfigManager().getMessage(damager, "no-permission"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        Island island = plugin.getIslandManager().getIslandAt(entity.getLocation());
        if (island == null) return;

        if (entity instanceof Monster || entity instanceof Slime || entity instanceof Phantom) {
            if (!island.getBasicSetting("mob-spawn")) {
                // Cancel natural spawning
                if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL || 
                    event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
                    event.setCancelled(true);
                }
            }
        } else if (entity instanceof Animals || entity instanceof WaterMob) {
            if (!island.getBasicSetting("animal-spawn")) {
                if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        UUID uuid = player.getUniqueId();
        boolean isMember = island.isMember(uuid);

        if (!isMember) {
            if (!island.getBasicSetting("block-break")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        UUID uuid = player.getUniqueId();
        boolean isMember = island.isMember(uuid);

        if (!isMember) {
            if (!island.getBasicSetting("block-place")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage(event.getPlayer(), "no-permission"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage(event.getPlayer(), "no-permission"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (event.getClickedBlock() == null) return;

        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            if (!canBuild(player, event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }

        Island island = plugin.getIslandManager().getIslandAt(event.getClickedBlock().getLocation());
        if (island == null) return;

        UUID uuid = player.getUniqueId();
        boolean isMember = island.isMember(uuid);

        if (!isMember) {
            Material type = event.getClickedBlock().getType();
            
            // Chest / Container opening check
            if (type.name().contains("CHEST") || type.name().contains("SHULKER_BOX") || 
                type == Material.FURNACE || type == Material.BLAST_FURNACE || 
                type == Material.SMOKER || type == Material.HOPPER || 
                type == Material.BARREL || type == Material.DISPENSER || 
                type == Material.DROPPER) {
                
                if (!island.getBasicSetting("chest-open")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
                }
            } 
            // Doors, buttons, gates, levers check
            else if (type.name().contains("DOOR") || type.name().contains("BUTTON") || 
                     type == Material.LEVER || type.name().contains("GATE")) {
                
                if (!island.getBasicSetting("interaction")) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getRightClicked().getLocation());
        if (island == null || island.isMember(player.getUniqueId())) return;

        if (!island.getBasicSetting("interaction")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = getResponsiblePlayer(event.getRemover());
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        if (!canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        if (!canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
        }
    }

    // ==========================================
    // Premium Settings
    // ==========================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreeperExplode(EntityExplodeEvent event) {
        Island island = plugin.getIslandManager().getIslandAt(event.getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("creeper-explosion")) {
            // Cancel block damage but allow particle/sound
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("creeper-explosion")) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEndermanGrief(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.ENDERMAN) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("enderman-grief")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFireSpread(BlockSpreadEvent event) {
        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("fire-spread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBurn(BlockBurnEvent event) {
        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("fire-spread")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFireIgnite(BlockIgniteEvent event) {
        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("fire-spread")) {
            Player player = event.getPlayer();
            if (event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                    && player != null
                    && island.isMember(player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeafDecay(LeavesDecayEvent event) {
        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("leaf-decay")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Island island = plugin.getIslandManager().getIslandAt(player.getLocation());
        if (island == null) return;

        if (island.getPremiumSetting("keep-inventory")) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Island island = plugin.getIslandManager().getIslandAt(player.getLocation());
        if (island == null) return;

        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
        }

        if (damager instanceof Monster || damager instanceof Slime || damager instanceof Phantom) {
            if (!island.getPremiumSetting("mob-damage")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        Island island = plugin.getIslandManager().getIslandAt(player.getLocation());
        if (island == null) return;

        if (!island.getPremiumSetting("fall-damage")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.getIslandManager().getIslandByPlayer(player.getUniqueId());
        if (island != null) {
            event.setRespawnLocation(island.getHome(plugin.getWorldManager().getAcidWorld()));
        } else {
            event.setRespawnLocation(plugin.getLobbyLocation());
        }
    }

    private boolean canBuild(Player player, Location location) {
        Island island = plugin.getIslandManager().getIslandAt(location);
        if (island == null || island.isMember(player.getUniqueId())) {
            return true;
        }
        return island.getBasicSetting("block-place") && island.getBasicSetting("block-break");
    }

    private Player getResponsiblePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void enablePluginFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pluginFlightPlayers.contains(uuid)) {
            previousAllowFlight.put(uuid, player.getAllowFlight());
            pluginFlightPlayers.add(uuid);
        }
        player.setAllowFlight(true);
    }

    private void disablePluginFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pluginFlightPlayers.remove(uuid)) {
            return;
        }
        boolean previous = previousAllowFlight.getOrDefault(uuid, false);
        previousAllowFlight.remove(uuid);
        if (!previous && player.getAllowFlight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        } else if (previous) {
            player.setAllowFlight(true);
        }
    }
}
