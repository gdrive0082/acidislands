package id.alvarennation.acidIsland.island;

import id.alvarennation.acidIsland.AcidIsland;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IslandManager {

    private final AcidIsland plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, Island> islandsByOwner = new HashMap<>();
    private final Map<UUID, UUID> memberToOwnerMap = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private final Map<String, Island> islandsByGrid = new HashMap<>();
    private final Map<UUID, Long> lastIslandCreateMillis = new HashMap<>();
    private final Map<UUID, Long> lastIslandDeleteMillis = new HashMap<>();
    private final Map<UUID, Integer> storyStages = new HashMap<>();
    private final Set<UUID> pendingIslandCreates = new HashSet<>();

    private int nextGridIndex = 0;

    public IslandManager(AcidIsland plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "islands.yml");
        loadData();
    }

    public void loadData() {
        if (plugin.getWorldManager() != null) {
            plugin.getWorldManager().cancelIslandValueScans();
        }
        islandsByOwner.clear();
        memberToOwnerMap.clear();
        pendingInvites.clear();
        islandsByGrid.clear();
        lastIslandCreateMillis.clear();
        lastIslandDeleteMillis.clear();
        storyStages.clear();
        pendingIslandCreates.clear();

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        nextGridIndex = dataConfig.getInt(
                "next-grid-index",
                plugin.getConfigManager().getConfig().getInt("island-start-grid-index", 1)
        );
        loadCooldowns();
        loadPlayerProgress();

        ConfigurationSection section = dataConfig.getConfigurationSection("islands");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            UUID ownerUuid;
            try {
                ownerUuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid island owner UUID in islands.yml: " + key);
                continue;
            }
            int x = section.getInt(key + ".x");
            int z = section.getInt(key + ".z");

            Island island = new Island(ownerUuid, x, z);
            island.setTheme(section.getString(key + ".theme", "classic"));

            if (section.contains(key + ".home")) {
                double hx = section.getDouble(key + ".home.x");
                double hy = section.getDouble(key + ".home.y");
                double hz = section.getDouble(key + ".home.z");
                float hyaw = (float) section.getDouble(key + ".home.yaw");
                float hpitch = (float) section.getDouble(key + ".home.pitch");
                World world = plugin.getWorldManager().getAcidWorld();
                island.setHome(new Location(world, hx, hy, hz, hyaw, hpitch));
            }

            List<String> membersList = section.getStringList(key + ".members");
            ConfigurationSection roleSection = section.getConfigurationSection(key + ".member-roles");
            for (String memberStr : membersList) {
                UUID memberUuid;
                try {
                    memberUuid = UUID.fromString(memberStr);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping invalid member UUID '" + memberStr + "' in island " + key + ".");
                    continue;
                }
                String rawRole = roleSection == null ? "MEMBER" : roleSection.getString(memberStr, "MEMBER");
                island.addMember(memberUuid, IslandRole.fromString(rawRole));
                memberToOwnerMap.put(memberUuid, ownerUuid);
            }

            ConfigurationSection upSection = section.getConfigurationSection(key + ".upgrades");
            if (upSection != null) {
                for (String upKey : upSection.getKeys(false)) {
                    island.setLevel(upKey, upSection.getInt(upKey));
                }
            }

            ConfigurationSection basicSec = section.getConfigurationSection(key + ".settings.basic");
            if (basicSec != null) {
                for (String setKey : basicSec.getKeys(false)) {
                    island.setBasicSetting(setKey, basicSec.getBoolean(setKey));
                }
            }

            ConfigurationSection premSec = section.getConfigurationSection(key + ".settings.premium");
            if (premSec != null) {
                for (String setKey : premSec.getKeys(false)) {
                    island.setPremiumSetting(setKey, premSec.getBoolean(setKey));
                }
            }

            for (String questId : section.getStringList(key + ".completed-quests")) {
                island.completeQuest(questId);
            }

            island.setBankBalance(section.getDouble(key + ".bank", 0.0));
            island.setVaultBase64(section.getString(key + ".vault", ""));
            islandsByOwner.put(ownerUuid, island);
            indexIsland(island);
        }
    }

    public void saveData() {
        dataConfig.set("next-grid-index", nextGridIndex);
        saveCooldowns();
        savePlayerProgress();

        for (Map.Entry<UUID, Island> entry : islandsByOwner.entrySet()) {
            UUID ownerUuid = entry.getKey();
            Island island = entry.getValue();
            String path = "islands." + ownerUuid;

            dataConfig.set(path + ".x", island.getX());
            dataConfig.set(path + ".z", island.getZ());
            dataConfig.set(path + ".theme", island.getTheme());

            World world = plugin.getWorldManager().getAcidWorld();
            Location home = island.getHome(world);
            dataConfig.set(path + ".home.x", home.getX());
            dataConfig.set(path + ".home.y", home.getY());
            dataConfig.set(path + ".home.z", home.getZ());
            dataConfig.set(path + ".home.yaw", home.getYaw());
            dataConfig.set(path + ".home.pitch", home.getPitch());

            List<String> membersList = new ArrayList<>();
            dataConfig.set(path + ".member-roles", null);
            for (UUID memberUuid : island.getMembers()) {
                membersList.add(memberUuid.toString());
                dataConfig.set(path + ".member-roles." + memberUuid, island.getRole(memberUuid).name());
            }
            dataConfig.set(path + ".members", membersList);

            for (Map.Entry<String, Integer> upEntry : island.getLevels().entrySet()) {
                dataConfig.set(path + ".upgrades." + upEntry.getKey(), upEntry.getValue());
            }

            for (Map.Entry<String, Boolean> setEntry : island.getBasicSettings().entrySet()) {
                dataConfig.set(path + ".settings.basic." + setEntry.getKey(), setEntry.getValue());
            }

            for (Map.Entry<String, Boolean> setEntry : island.getPremiumSettings().entrySet()) {
                dataConfig.set(path + ".settings.premium." + setEntry.getKey(), setEntry.getValue());
            }

            dataConfig.set(path + ".completed-quests", new ArrayList<>(island.getCompletedQuests()));
            dataConfig.set(path + ".bank", island.getBankBalance());
            dataConfig.set(path + ".vault", island.getVaultBase64());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Island getIslandByOwner(UUID ownerUuid) {
        return islandsByOwner.get(ownerUuid);
    }

    public Island getIslandByPlayer(UUID playerUuid) {
        if (islandsByOwner.containsKey(playerUuid)) {
            return islandsByOwner.get(playerUuid);
        }
        UUID ownerUuid = memberToOwnerMap.get(playerUuid);
        return ownerUuid == null ? null : islandsByOwner.get(ownerUuid);
    }

    public List<Island> getAllIslands() {
        return new ArrayList<>(islandsByOwner.values());
    }

    public boolean hasIsland(UUID uuid) {
        return getIslandByPlayer(uuid) != null;
    }

    public boolean isIslandCreatePending(UUID ownerUuid) {
        return pendingIslandCreates.contains(ownerUuid);
    }

    public Island createIsland(UUID ownerUuid, String type) {
        return createIsland(ownerUuid, type, true);
    }

    public Island createIsland(UUID ownerUuid, String type, boolean recordCooldown) {
        if (islandsByOwner.containsKey(ownerUuid)) {
            throw new IllegalStateException("Player already owns an island: " + ownerUuid);
        }
        int spacing = plugin.getConfigManager().getConfig().getInt("island-spacing", 400);
        int[] grid = gridForIndex(nextGridIndex);
        int cx = grid[0] * spacing;
        int cz = grid[1] * spacing;
        nextGridIndex++;

        plugin.getWorldManager().generateStarterIsland(cx, cz, type);

        Island island = new Island(ownerUuid, cx, cz);
        island.setTheme(type);
        islandsByOwner.put(ownerUuid, island);
        indexIsland(island);
        if (recordCooldown) {
            lastIslandCreateMillis.put(ownerUuid, System.currentTimeMillis());
        }
        plugin.getWorldManager().applyIslandTheme(island, type);
        saveData();

        return island;
    }

    public CompletableFuture<Island> createIslandAsync(UUID ownerUuid, String type, boolean recordCooldown) {
        CompletableFuture<Island> result = new CompletableFuture<>();
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> createIslandAsync(ownerUuid, type, recordCooldown)
                    .whenComplete((island, throwable) -> {
                        if (throwable != null) {
                            result.completeExceptionally(throwable);
                        } else {
                            result.complete(island);
                        }
                    }));
            return result;
        }

        if (islandsByOwner.containsKey(ownerUuid)) {
            result.completeExceptionally(new IllegalStateException("Player already owns an island: " + ownerUuid));
            return result;
        }
        if (pendingIslandCreates.contains(ownerUuid)) {
            result.completeExceptionally(new IllegalStateException("Island creation is already running for: " + ownerUuid));
            return result;
        }

        int spacing = plugin.getConfigManager().getConfig().getInt("island-spacing", 400);
        int[] grid = gridForIndex(nextGridIndex);
        int cx = grid[0] * spacing;
        int cz = grid[1] * spacing;
        nextGridIndex++;
        pendingIslandCreates.add(ownerUuid);

        plugin.getWorldManager().preloadStarterIslandChunks(cx, cz).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (throwable != null) {
                            result.completeExceptionally(throwable);
                            return;
                        }

                        plugin.getWorldManager().generateStarterIsland(cx, cz, type);

                        Island island = new Island(ownerUuid, cx, cz);
                        island.setTheme(type);
                        islandsByOwner.put(ownerUuid, island);
                        indexIsland(island);
                        if (recordCooldown) {
                            lastIslandCreateMillis.put(ownerUuid, System.currentTimeMillis());
                        }
                        plugin.getWorldManager().applyIslandTheme(island, type);
                        saveData();
                        result.complete(island);
                    } catch (Throwable ex) {
                        result.completeExceptionally(ex);
                    } finally {
                        pendingIslandCreates.remove(ownerUuid);
                    }
                }));

        return result;
    }

    public Island deleteIsland(UUID ownerUuid) {
        return deleteIsland(ownerUuid, true);
    }

    public Island deleteIsland(UUID ownerUuid, boolean cleanupWorld) {
        return deleteIsland(ownerUuid, cleanupWorld, true);
    }

    public Island deleteIsland(UUID ownerUuid, boolean cleanupWorld, boolean recordCooldown) {
        if (plugin.getIslandGUI() != null) {
            plugin.getIslandGUI().closeAndDiscardVault(ownerUuid);
        }
        Island island = islandsByOwner.remove(ownerUuid);
        if (island == null) {
            return null;
        }
        removeIslandIndex(island);

        for (UUID memberUuid : island.getMembers()) {
            memberToOwnerMap.remove(memberUuid);
        }
        pendingInvites.entrySet().removeIf(entry -> entry.getKey().equals(ownerUuid)
                || entry.getValue().equals(ownerUuid)
                || island.getMembers().contains(entry.getKey()));
        dataConfig.set("islands." + ownerUuid, null);

        if (cleanupWorld) {
            plugin.getWorldManager().scheduleIslandCleanup(island);
        }
        if (recordCooldown) {
            lastIslandDeleteMillis.put(ownerUuid, System.currentTimeMillis());
        }
        saveData();
        return island;
    }

    public void addMember(Island island, UUID memberUuid) {
        island.addMember(memberUuid);
        memberToOwnerMap.put(memberUuid, island.getOwner());
        saveData();
    }

    public void removeMember(Island island, UUID memberUuid) {
        island.removeMember(memberUuid);
        memberToOwnerMap.remove(memberUuid);
        saveData();
    }

    public void removePlayerFromCurrentIsland(UUID playerUuid) {
        Island current = getIslandByPlayer(playerUuid);
        if (current == null || current.isOwner(playerUuid)) {
            return;
        }
        removeMember(current, playerUuid);
    }

    public void setMemberRole(Island island, UUID memberUuid, IslandRole role) {
        island.setMemberRole(memberUuid, role);
        saveData();
    }

    public void addInvite(UUID invited, UUID owner) {
        pendingInvites.put(invited, owner);
    }

    public UUID getPendingInvite(UUID invited) {
        return pendingInvites.get(invited);
    }

    public void removeInvite(UUID invited) {
        pendingInvites.remove(invited);
    }

    public int getStoryStage(UUID playerUuid) {
        return storyStages.getOrDefault(playerUuid, 0);
    }

    public int getIslandStoryStage(Island island) {
        int highest = getStoryStage(island.getOwner());
        for (UUID memberUuid : island.getMembers()) {
            highest = Math.max(highest, getStoryStage(memberUuid));
        }
        return highest;
    }

    public void setStoryStage(UUID playerUuid, int stage) {
        int normalizedStage = Math.max(0, stage);
        storyStages.put(playerUuid, normalizedStage);
        saveData();
        if (plugin.getConverseCraftHook() != null) {
            plugin.getConverseCraftHook().syncStoryStage(playerUuid, normalizedStage);
        }
    }

    public Map<UUID, Integer> getStoryStagesSnapshot() {
        return new HashMap<>(storyStages);
    }

    public boolean canCreateIsland(UUID playerUuid) {
        return getCreateCooldownRemainingMillis(playerUuid) <= 0L;
    }

    public long getCreateCooldownRemainingMillis(UUID playerUuid) {
        long now = System.currentTimeMillis();
        long createCooldown = plugin.getConfigManager().getConfig().getLong("cooldowns.create-seconds", 1800L) * 1000L;
        long deleteCooldown = plugin.getConfigManager().getConfig().getLong("cooldowns.delete-seconds", 1800L) * 1000L;
        long lastCreate = lastIslandCreateMillis.getOrDefault(playerUuid, 0L);
        long lastDelete = lastIslandDeleteMillis.getOrDefault(playerUuid, 0L);
        long createRemaining = createCooldown - (now - lastCreate);
        long deleteRemaining = deleteCooldown - (now - lastDelete);
        return Math.max(0L, Math.max(createRemaining, deleteRemaining));
    }

    public boolean canDeleteIsland(UUID playerUuid) {
        return getDeleteCooldownRemainingMillis(playerUuid) <= 0L;
    }

    public long getDeleteCooldownRemainingMillis(UUID playerUuid) {
        long cooldown = plugin.getConfigManager().getConfig().getLong("cooldowns.delete-seconds", 1800L) * 1000L;
        long lastDelete = lastIslandDeleteMillis.getOrDefault(playerUuid, 0L);
        return Math.max(0L, cooldown - (System.currentTimeMillis() - lastDelete));
    }

    public Island getIslandAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String wName = plugin.getConfigManager().getConfig().getString("world-name", "acid_island_world");
        if (!loc.getWorld().getName().equals(wName)) return null;

        int spacing = getIslandSpacing();
        int gridX = gridCoordinate(loc.getX(), spacing);
        int gridZ = gridCoordinate(loc.getZ(), spacing);
        int searchRadius = Math.max(1, (int) Math.ceil((getMaxConfiguredBorderSize() / 2.0) / spacing) + 1);

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                Island island = islandsByGrid.get(gridKey(gridX + dx, gridZ + dz));
                if (island != null && isInsideIslandBounds(loc, island)) {
                    return island;
                }
            }
        }
        return null;
    }

    public int getBorderSize(Island island) {
        int level = island.getLevel("border");
        return plugin.getConfigManager().getConfig().getInt("upgrades.border." + level + ".size", 50);
    }

    public long getIslandValue(Island island, boolean forceRefresh) {
        return getIslandValue(island, forceRefresh, true);
    }

    public long getIslandValue(Island island, boolean forceRefresh, boolean allowSchedule) {
        int cacheSeconds = plugin.getConfigManager().getConfig().getInt("level.cache-seconds", 300);
        long cacheAge = System.currentTimeMillis() - island.getLastLevelScanMillis();
        if (!forceRefresh && island.getCachedIslandValue() >= 0 && cacheAge < cacheSeconds * 1000L) {
            return island.getCachedIslandValue();
        }
        if (allowSchedule) {
            plugin.getWorldManager().scheduleIslandValueScan(island);
        }
        return Math.max(0L, island.getCachedIslandValue());
    }

    public int getIslandLevel(Island island, boolean forceRefresh) {
        getIslandValue(island, forceRefresh, true);
        return island.getCachedIslandLevel();
    }

    public int getIslandLevel(Island island, boolean forceRefresh, boolean allowSchedule) {
        getIslandValue(island, forceRefresh, allowSchedule);
        return island.getCachedIslandLevel();
    }

    public List<IslandRanking> getTopIslands(int limit) {
        List<IslandRanking> rankings = new ArrayList<>();
        int scheduled = 0;
        for (Island island : islandsByOwner.values()) {
            boolean allowSchedule = scheduled < limit;
            long value = getIslandValue(island, false, allowSchedule);
            if (allowSchedule && island.isLevelScanInProgress()) {
                scheduled++;
            }
            rankings.add(new IslandRanking(island, value, island.getCachedIslandLevel()));
        }
        rankings.sort(Comparator.comparingLong(IslandRanking::value).reversed());
        return rankings.subList(0, Math.min(limit, rankings.size()));
    }

    private int[] gridForIndex(int index) {
        if (index <= 0) {
            return new int[]{0, 0};
        }

        int layer = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2.0);
        int legLength = layer * 2;
        int maxValue = (layer * 2 + 1) * (layer * 2 + 1) - 1;
        int offset = maxValue - index;

        if (offset < legLength) {
            return new int[]{layer - offset, -layer};
        }
        offset -= legLength;
        if (offset < legLength) {
            return new int[]{-layer, -layer + offset};
        }
        offset -= legLength;
        if (offset < legLength) {
            return new int[]{-layer + offset, layer};
        }
        offset -= legLength;
        return new int[]{layer, layer - offset};
    }

    private void indexIsland(Island island) {
        int spacing = getIslandSpacing();
        islandsByGrid.put(gridKey(gridCoordinate(island.getX(), spacing), gridCoordinate(island.getZ(), spacing)), island);
    }

    private void removeIslandIndex(Island island) {
        int spacing = getIslandSpacing();
        islandsByGrid.remove(gridKey(gridCoordinate(island.getX(), spacing), gridCoordinate(island.getZ(), spacing)), island);
    }

    private boolean isInsideIslandBounds(Location loc, Island island) {
        int borderSize = getBorderSize(island);
        double half = borderSize / 2.0;
        return loc.getX() >= island.getX() - half && loc.getX() <= island.getX() + half
                && loc.getZ() >= island.getZ() - half && loc.getZ() <= island.getZ() + half;
    }

    private int getIslandSpacing() {
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("island-spacing", 400));
    }

    private int getMaxConfiguredBorderSize() {
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection("upgrades.border");
        if (section == null) {
            return 50;
        }
        int max = 50;
        for (String key : section.getKeys(false)) {
            max = Math.max(max, section.getInt(key + ".size", 50));
        }
        return max;
    }

    private int gridCoordinate(double coordinate, int spacing) {
        return (int) Math.round(coordinate / spacing);
    }

    private String gridKey(int gridX, int gridZ) {
        return gridX + ":" + gridZ;
    }

    private void loadCooldowns() {
        ConfigurationSection section = dataConfig.getConfigurationSection("cooldowns");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                lastIslandCreateMillis.put(uuid, section.getLong(key + ".last-create", 0L));
                lastIslandDeleteMillis.put(uuid, section.getLong(key + ".last-delete", 0L));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid cooldown UUID in islands.yml: " + key);
            }
        }
    }

    private void saveCooldowns() {
        dataConfig.set("cooldowns", null);
        for (Map.Entry<UUID, Long> entry : lastIslandCreateMillis.entrySet()) {
            dataConfig.set("cooldowns." + entry.getKey() + ".last-create", entry.getValue());
        }
        for (Map.Entry<UUID, Long> entry : lastIslandDeleteMillis.entrySet()) {
            dataConfig.set("cooldowns." + entry.getKey() + ".last-delete", entry.getValue());
        }
    }

    private void loadPlayerProgress() {
        ConfigurationSection section = dataConfig.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                storyStages.put(uuid, Math.max(0, section.getInt(key + ".story-stage", 0)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid player UUID in islands.yml: " + key);
            }
        }
    }

    private void savePlayerProgress() {
        dataConfig.set("players", null);
        for (Map.Entry<UUID, Integer> entry : storyStages.entrySet()) {
            dataConfig.set("players." + entry.getKey() + ".story-stage", Math.max(0, entry.getValue()));
        }
    }

    public record IslandRanking(Island island, long value, int level) {
    }
}
