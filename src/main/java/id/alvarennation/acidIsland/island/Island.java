package id.alvarennation.acidIsland.island;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Island {

    private final UUID owner;
    private final Set<UUID> members = new HashSet<>();
    private final Map<UUID, IslandRole> memberRoles = new HashMap<>();
    private final int x;
    private final int z;

    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;

    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, Boolean> basicSettings = new HashMap<>();
    private final Map<String, Boolean> premiumSettings = new HashMap<>();
    private final Set<String> completedQuests = new HashSet<>();

    private double bankBalance;
    private String vaultBase64 = "";
    private String theme = "classic";

    private long cachedIslandValue = -1L;
    private int cachedIslandLevel = 0;
    private long lastLevelScanMillis = 0L;

    public Island(UUID owner, int x, int z) {
        this.owner = owner;
        this.x = x;
        this.z = z;

        this.homeX = x;
        this.homeY = 76;
        this.homeZ = z;
        this.homeYaw = 0;
        this.homePitch = 0;

        levels.put("border", 1);
        levels.put("members", 1);
        levels.put("vault", 1);
        levels.put("minions", 1);
        levels.put("bank", 1);
        levels.put("generator", 1);

        basicSettings.put("pvp", false);
        basicSettings.put("mob-spawn", true);
        basicSettings.put("animal-spawn", true);
        basicSettings.put("block-break", false);
        basicSettings.put("block-place", false);
        basicSettings.put("chest-open", false);
        basicSettings.put("interaction", false);

        premiumSettings.put("creeper-explosion", false);
        premiumSettings.put("enderman-grief", false);
        premiumSettings.put("fire-spread", false);
        premiumSettings.put("leaf-decay", true);
        premiumSettings.put("weather-lock", false);
        premiumSettings.put("time-lock", false);
        premiumSettings.put("keep-inventory", false);
        premiumSettings.put("fly-mode", false);
        premiumSettings.put("mob-damage", true);
        premiumSettings.put("fall-damage", true);

        this.bankBalance = 0.0;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Map<UUID, IslandRole> getMemberRoles() {
        return memberRoles;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public Location getHome(World world) {
        return new Location(world, homeX, homeY, homeZ, homeYaw, homePitch);
    }

    public void setHome(Location loc) {
        this.homeX = loc.getX();
        this.homeY = loc.getY();
        this.homeZ = loc.getZ();
        this.homeYaw = loc.getYaw();
        this.homePitch = loc.getPitch();
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || members.contains(uuid);
    }

    public IslandRole getRole(UUID uuid) {
        if (owner.equals(uuid)) {
            return IslandRole.OWNER;
        }
        if (!members.contains(uuid)) {
            return IslandRole.VISITOR;
        }
        return memberRoles.getOrDefault(uuid, IslandRole.MEMBER);
    }

    public boolean hasRole(UUID uuid, IslandRole role) {
        return getRole(uuid).atLeast(role);
    }

    public boolean canManage(UUID uuid) {
        return hasRole(uuid, IslandRole.CO_OWNER);
    }

    public boolean canUseBank(UUID uuid) {
        return hasRole(uuid, IslandRole.CO_OWNER);
    }

    public boolean canChangeRoles(UUID uuid) {
        return isOwner(uuid);
    }

    public void addMember(UUID memberUuid) {
        addMember(memberUuid, IslandRole.MEMBER);
    }

    public void addMember(UUID memberUuid, IslandRole role) {
        members.add(memberUuid);
        memberRoles.put(memberUuid, role == IslandRole.OWNER ? IslandRole.CO_OWNER : role);
    }

    public void removeMember(UUID memberUuid) {
        members.remove(memberUuid);
        memberRoles.remove(memberUuid);
    }

    public void setMemberRole(UUID memberUuid, IslandRole role) {
        if (members.contains(memberUuid)) {
            memberRoles.put(memberUuid, role == IslandRole.OWNER ? IslandRole.CO_OWNER : role);
        }
    }

    public Set<UUID> getParticipants() {
        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(owner);
        participants.addAll(members);
        return participants;
    }

    public int getLevel(String upgradeType) {
        return levels.getOrDefault(upgradeType, 1);
    }

    public void setLevel(String upgradeType, int level) {
        levels.put(upgradeType, level);
        invalidateLevelCache();
    }

    public Map<String, Integer> getLevels() {
        return levels;
    }

    public boolean getBasicSetting(String setting) {
        return basicSettings.getOrDefault(setting, false);
    }

    public void setBasicSetting(String setting, boolean val) {
        basicSettings.put(setting, val);
    }

    public Map<String, Boolean> getBasicSettings() {
        return basicSettings;
    }

    public boolean getPremiumSetting(String setting) {
        return premiumSettings.getOrDefault(setting, false);
    }

    public void setPremiumSetting(String setting, boolean val) {
        premiumSettings.put(setting, val);
    }

    public Map<String, Boolean> getPremiumSettings() {
        return premiumSettings;
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = bankBalance;
    }

    public String getVaultBase64() {
        return vaultBase64;
    }

    public void setVaultBase64(String vaultBase64) {
        this.vaultBase64 = vaultBase64;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme == null || theme.isBlank() ? "classic" : theme.toLowerCase();
    }

    public Set<String> getCompletedQuests() {
        return completedQuests;
    }

    public boolean hasCompletedQuest(String questId) {
        return completedQuests.contains(questId.toLowerCase());
    }

    public void completeQuest(String questId) {
        completedQuests.add(questId.toLowerCase());
    }

    public long getCachedIslandValue() {
        return cachedIslandValue;
    }

    public int getCachedIslandLevel() {
        return cachedIslandLevel;
    }

    public long getLastLevelScanMillis() {
        return lastLevelScanMillis;
    }

    public void setLevelCache(long value, int level) {
        this.cachedIslandValue = value;
        this.cachedIslandLevel = level;
        this.lastLevelScanMillis = System.currentTimeMillis();
    }

    public void invalidateLevelCache() {
        this.lastLevelScanMillis = 0L;
    }

    public static String itemStackArrayToBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) {
        try {
            if (data == null || data.isEmpty()) return new ItemStack[0];
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }
}
