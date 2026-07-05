package id.alvarennation.acidIsland.listeners;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
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
    private final Map<UUID, Long> flightDisableGraceUntil = new HashMap<>();
    private final Map<UUID, Long> lastProtectionMessage = new HashMap<>();

    public IslandProtectionListener(AcidIsland plugin) {
        this.plugin = plugin;
        startIslandTracker();
    }

    private void startIslandTracker() {
        // Task periodik untuk Fly Mode, Weather Lock, dan Time Lock
        new BukkitRunnable() {
            @Override
            public void run() {
                String acidWorldName = plugin.getConfigManager().getAcidWorldName();
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)) return;

        Player damager = getResponsiblePlayer(event);

        if (damager == null) return;

        Island island = plugin.getIslandManager().getIslandAt(damaged.getLocation());
        if (island != null) {
            if (!island.getBasicSetting("pvp")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVisitorEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) return;

        Player damager = getResponsiblePlayer(event);
        if (damager == null || damager.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getEntity().getLocation());
        if (island == null || island.isMember(damager.getUniqueId())) return;

        event.setCancelled(true);
        sendProtectionMessage(damager);
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
                sendProtectionMessage(player);
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
                sendProtectionMessage(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(event.getPlayer());
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
            
            if (event.getClickedBlock().getState() instanceof Container) {
                if (!island.getBasicSetting("chest-open")) {
                    event.setCancelled(true);
                    sendProtectionMessage(player);
                }
            } else if (!isAlwaysAllowedInteraction(type)) {
                if (!island.getBasicSetting("interaction")) {
                    event.setCancelled(true);
                    sendProtectionMessage(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getRightClicked().getLocation());
        if (island == null || island.isMember(player.getUniqueId())) return;

        if (!island.getBasicSetting("interaction")) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getRightClicked().getLocation());
        if (island == null || island.isMember(player.getUniqueId())) return;

        if (!island.getBasicSetting("interaction")) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = getResponsiblePlayer(event.getRemover());
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        if (!canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        if (!canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = getResponsiblePlayer(event.getAttacker());
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (!canBuild(player, event.getVehicle().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = getResponsiblePlayer(event.getAttacker());
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (!canBuild(player, event.getVehicle().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getEntity().getLocation());
        if (island == null || island.isMember(player.getUniqueId())) return;
        if (!island.getBasicSetting("interaction")) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!canBuild(event.getPlayer(), event.getHarvestedBlock().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTakeLecternBook(PlayerTakeLecternBookEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.getIslandManager().getIslandAt(event.getLectern().getBlock().getLocation());
        if (island == null || island.isMember(player.getUniqueId())) return;
        if (!island.getBasicSetting("interaction")) {
            event.setCancelled(true);
            sendProtectionMessage(player);
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
        if (!player.getWorld().equals(plugin.getWorldManager().getAcidWorld())) {
            return;
        }
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
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

    private boolean isAlwaysAllowedInteraction(Material material) {
        return material == Material.CRAFTING_TABLE || material == Material.ENDER_CHEST;
    }

    private Player getResponsiblePlayer(EntityDamageByEntityEvent event) {
        try {
            Entity causingEntity = event.getDamageSource().getCausingEntity();
            if (causingEntity instanceof Player player) {
                return player;
            }
        } catch (RuntimeException ignored) {
        }
        return getResponsiblePlayer(event.getDamager());
    }

    private Player getResponsiblePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void sendProtectionMessage(Player player) {
        long now = System.currentTimeMillis();
        long last = lastProtectionMessage.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 2000L) {
            return;
        }
        lastProtectionMessage.put(player.getUniqueId(), now);
        player.sendMessage(plugin.getConfigManager().getMessage(player, "no-permission"));
    }

    private void enablePluginFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pluginFlightPlayers.contains(uuid)) {
            previousAllowFlight.put(uuid, player.getAllowFlight());
            pluginFlightPlayers.add(uuid);
        }
        flightDisableGraceUntil.remove(uuid);
        player.setAllowFlight(true);
    }

    private void disablePluginFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pluginFlightPlayers.remove(uuid)) {
            return;
        }
        if (player.isFlying()
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            long now = System.currentTimeMillis();
            long until = flightDisableGraceUntil.getOrDefault(uuid, 0L);
            if (until == 0L) {
                flightDisableGraceUntil.put(uuid, now + 5000L);
                pluginFlightPlayers.add(uuid);
                player.sendMessage(plugin.getConfigManager().format("&eFly nonaktif dalam 5 detik!"));
                return;
            }
            if (now < until) {
                pluginFlightPlayers.add(uuid);
                return;
            }
            flightDisableGraceUntil.remove(uuid);
            player.setFallDistance(0.0f);
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        disablePluginFlight(event.getPlayer());
        pluginFlightPlayers.remove(uuid);
        previousAllowFlight.remove(uuid);
        flightDisableGraceUntil.remove(uuid);
        lastProtectionMessage.remove(uuid);
    }
}
