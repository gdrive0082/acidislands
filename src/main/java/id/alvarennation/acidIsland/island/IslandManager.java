package id.alvarennation.acidIsland.island;

import id.alvarennation.acidIsland.AcidIsland;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IslandManager {

    private final AcidIsland plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, Island> islandsByOwner = new HashMap<>();
    private final Map<UUID, UUID> memberToOwnerMap = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    private int nextGridIndex = 0;

    public IslandManager(AcidIsland plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "islands.yml");
        loadData();
    }

    public void loadData() {
        islandsByOwner.clear();
        memberToOwnerMap.clear();
        pendingInvites.clear();

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        nextGridIndex = dataConfig.getInt("next-grid-index", 0);

        ConfigurationSection section = dataConfig.getConfigurationSection("islands");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            UUID ownerUuid = UUID.fromString(key);
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
                UUID memberUuid = UUID.fromString(memberStr);
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
        }
    }

    public void saveData() {
        dataConfig.set("next-grid-index", nextGridIndex);

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

    public Island createIsland(UUID ownerUuid, String type) {
        int spacing = plugin.getConfigManager().getConfig().getInt("island-spacing", 400);
        int[] grid = gridForIndex(nextGridIndex);
        int cx = grid[0] * spacing;
        int cz = grid[1] * spacing;
        nextGridIndex++;

        plugin.getWorldManager().generateStarterIsland(cx, cz, type);

        Island island = new Island(ownerUuid, cx, cz);
        island.setTheme(type);
        islandsByOwner.put(ownerUuid, island);
        plugin.getWorldManager().applyIslandTheme(island, type);
        saveData();

        return island;
    }

    public Island deleteIsland(UUID ownerUuid) {
        return deleteIsland(ownerUuid, true);
    }

    public Island deleteIsland(UUID ownerUuid, boolean cleanupWorld) {
        Island island = islandsByOwner.remove(ownerUuid);
        if (island == null) {
            return null;
        }

        for (UUID memberUuid : island.getMembers()) {
            memberToOwnerMap.remove(memberUuid);
        }
        pendingInvites.entrySet().removeIf(entry -> entry.getKey().equals(ownerUuid)
                || entry.getValue().equals(ownerUuid)
                || island.getMembers().contains(entry.getKey()));
        dataConfig.set("islands." + ownerUuid, null);
        saveData();

        if (cleanupWorld) {
            plugin.getWorldManager().scheduleIslandCleanup(island);
        }
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

    public Island getIslandAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String wName = plugin.getConfigManager().getConfig().getString("world-name", "acid_island_world");
        if (!loc.getWorld().getName().equals(wName)) return null;

        for (Island island : islandsByOwner.values()) {
            int borderSize = getBorderSize(island);
            double half = borderSize / 2.0;
            if (loc.getX() >= island.getX() - half && loc.getX() <= island.getX() + half
                    && loc.getZ() >= island.getZ() - half && loc.getZ() <= island.getZ() + half) {
                return island;
            }
        }
        return null;
    }

    public int getBorderSize(Island island) {
        int level = island.getLevel("border");
        return plugin.getConfigManager().getConfig().getInt("upgrades.border." + level + ".size", 50);
    }

    public long getIslandValue(Island island, boolean forceRefresh) {
        int cacheSeconds = plugin.getConfigManager().getConfig().getInt("level.cache-seconds", 300);
        long cacheAge = System.currentTimeMillis() - island.getLastLevelScanMillis();
        if (!forceRefresh && island.getCachedIslandValue() >= 0 && cacheAge < cacheSeconds * 1000L) {
            return island.getCachedIslandValue();
        }
        long value = plugin.getWorldManager().calculateIslandValue(island);
        int pointsPerLevel = Math.max(1, plugin.getConfigManager().getConfig().getInt("level.points-per-level", 100));
        int level = (int) Math.max(0, value / pointsPerLevel);
        island.setLevelCache(value, level);
        return value;
    }

    public int getIslandLevel(Island island, boolean forceRefresh) {
        getIslandValue(island, forceRefresh);
        return island.getCachedIslandLevel();
    }

    public List<IslandRanking> getTopIslands(int limit) {
        List<IslandRanking> rankings = new ArrayList<>();
        for (Island island : islandsByOwner.values()) {
            long value = getIslandValue(island, false);
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

    public record IslandRanking(Island island, long value, int level) {
    }
}
