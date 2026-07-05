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
import java.util.*;

public class IslandManager {

    private final AcidIsland plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, Island> islandsByOwner = new HashMap<>();
    private final Map<UUID, UUID> memberToOwnerMap = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>(); // Invited -> Owner

    private int nextGridIndex = 0;

    public IslandManager(AcidIsland plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "islands.yml");
        loadData();
    }

    public void loadData() {
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
        if (section != null) {
            for (String key : section.getKeys(false)) {
                UUID ownerUuid = UUID.fromString(key);
                int x = section.getInt(key + ".x");
                int z = section.getInt(key + ".z");

                Island island = new Island(ownerUuid, x, z);

                // Load home
                if (section.contains(key + ".home")) {
                    double hx = section.getDouble(key + ".home.x");
                    double hy = section.getDouble(key + ".home.y");
                    double hz = section.getDouble(key + ".home.z");
                    float hyaw = (float) section.getDouble(key + ".home.yaw");
                    float hpitch = (float) section.getDouble(key + ".home.pitch");
                    
                    World world = plugin.getWorldManager().getAcidWorld();
                    island.setHome(new Location(world, hx, hy, hz, hyaw, hpitch));
                }

                // Load members
                List<String> membersList = section.getStringList(key + ".members");
                for (String memberStr : membersList) {
                    UUID memberUuid = UUID.fromString(memberStr);
                    island.getMembers().add(memberUuid);
                    memberToOwnerMap.put(memberUuid, ownerUuid);
                }

                // Load upgrades
                ConfigurationSection upSection = section.getConfigurationSection(key + ".upgrades");
                if (upSection != null) {
                    for (String upKey : upSection.getKeys(false)) {
                        island.setLevel(upKey, upSection.getInt(upKey));
                    }
                }

                // Load basic settings
                ConfigurationSection basicSec = section.getConfigurationSection(key + ".settings.basic");
                if (basicSec != null) {
                    for (String setKey : basicSec.getKeys(false)) {
                        island.setBasicSetting(setKey, basicSec.getBoolean(setKey));
                    }
                }

                // Load premium settings
                ConfigurationSection premSec = section.getConfigurationSection(key + ".settings.premium");
                if (premSec != null) {
                    for (String setKey : premSec.getKeys(false)) {
                        island.setPremiumSetting(setKey, premSec.getBoolean(setKey));
                    }
                }

                // Load bank balance
                island.setBankBalance(section.getDouble(key + ".bank", 0.0));

                // Load vault
                island.setVaultBase64(section.getString(key + ".vault", ""));

                islandsByOwner.put(ownerUuid, island);
            }
        }
    }

    public void saveData() {
        dataConfig.set("next-grid-index", nextGridIndex);

        for (Map.Entry<UUID, Island> entry : islandsByOwner.entrySet()) {
            UUID ownerUuid = entry.getKey();
            Island island = entry.getValue();
            String path = "islands." + ownerUuid.toString();

            dataConfig.set(path + ".x", island.getX());
            dataConfig.set(path + ".z", island.getZ());

            // Save home
            World world = plugin.getWorldManager().getAcidWorld();
            Location home = island.getHome(world);
            dataConfig.set(path + ".home.x", home.getX());
            dataConfig.set(path + ".home.y", home.getY());
            dataConfig.set(path + ".home.z", home.getZ());
            dataConfig.set(path + ".home.yaw", home.getYaw());
            dataConfig.set(path + ".home.pitch", home.getPitch());

            // Save members
            List<String> membersList = new ArrayList<>();
            for (UUID memberUuid : island.getMembers()) {
                membersList.add(memberUuid.toString());
            }
            dataConfig.set(path + ".members", membersList);

            // Save upgrades
            for (Map.Entry<String, Integer> upEntry : island.getLevels().entrySet()) {
                dataConfig.set(path + ".upgrades." + upEntry.getKey(), upEntry.getValue());
            }

            // Save basic settings
            for (Map.Entry<String, Boolean> setEntry : island.getBasicSettings().entrySet()) {
                dataConfig.set(path + ".settings.basic." + setEntry.getKey(), setEntry.getValue());
            }

            // Save premium settings
            for (Map.Entry<String, Boolean> setEntry : island.getPremiumSettings().entrySet()) {
                dataConfig.set(path + ".settings.premium." + setEntry.getKey(), setEntry.getValue());
            }

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
        if (ownerUuid != null) {
            return islandsByOwner.get(ownerUuid);
        }
        return null;
    }

    public boolean hasIsland(UUID uuid) {
        return getIslandByPlayer(uuid) != null;
    }

    public Island createIsland(UUID ownerUuid, String type) {
        int spacing = plugin.getConfigManager().getConfig().getInt("island-spacing", 400);
        int cx = nextGridIndex * spacing;
        int cz = 0;

        nextGridIndex++;

        // Generate the blocks
        plugin.getWorldManager().generateStarterIsland(cx, cz, type);

        Island island = new Island(ownerUuid, cx, cz);
        islandsByOwner.put(ownerUuid, island);
        saveData();

        return island;
    }

    public void deleteIsland(UUID ownerUuid) {
        Island island = islandsByOwner.remove(ownerUuid);
        if (island != null) {
            // Remove members from mapping
            for (UUID memberUuid : island.getMembers()) {
                memberToOwnerMap.remove(memberUuid);
            }
            // Save empty field to config to delete it
            dataConfig.set("islands." + ownerUuid.toString(), null);
            saveData();
        }
    }

    public void addMember(Island island, UUID memberUuid) {
        island.getMembers().add(memberUuid);
        memberToOwnerMap.put(memberUuid, island.getOwner());
        saveData();
    }

    public void removeMember(Island island, UUID memberUuid) {
        island.getMembers().remove(memberUuid);
        memberToOwnerMap.remove(memberUuid);
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

        int spacing = plugin.getConfigManager().getConfig().getInt("island-spacing", 400);
        int gridX = Math.round((float) loc.getX() / spacing);

        for (Island island : islandsByOwner.values()) {
            if (island.getX() == gridX * spacing) {
                int level = island.getLevel("border");
                int borderSize = plugin.getConfigManager().getConfig().getInt("upgrades.border." + level + ".size", 50);
                double minX = island.getX() - (borderSize / 2.0);
                double maxX = island.getX() + (borderSize / 2.0);
                double minZ = island.getZ() - (borderSize / 2.0);
                double maxZ = island.getZ() + (borderSize / 2.0);

                if (loc.getX() >= minX && loc.getX() <= maxX && loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                    return island;
                }
            }
        }
        return null;
    }
}
