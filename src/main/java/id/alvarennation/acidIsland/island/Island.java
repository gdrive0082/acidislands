package id.alvarennation.acidIsland.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class Island {

    private final UUID owner;
    private final Set<UUID> members = new HashSet<>();
    private final int x;
    private final int z;
    
    // Home location
    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;

    // Upgrades
    private final Map<String, Integer> levels = new HashMap<>();

    // Settings
    private final Map<String, Boolean> basicSettings = new HashMap<>();
    private final Map<String, Boolean> premiumSettings = new HashMap<>();

    // Bank
    private double bankBalance;

    // Vault Base64 Content
    private String vaultBase64 = "";

    public Island(UUID owner, int x, int z) {
        this.owner = owner;
        this.x = x;
        this.z = z;
        
        // Default home
        this.homeX = x;
        this.homeY = 76;
        this.homeZ = z;
        this.homeYaw = 0;
        this.homePitch = 0;

        // Default levels
        levels.put("border", 1);
        levels.put("members", 1);
        levels.put("vault", 1);
        levels.put("minions", 1);
        levels.put("bank", 1);
        levels.put("generator", 1);

        // Default basic settings
        basicSettings.put("pvp", false);
        basicSettings.put("mob-spawn", true);
        basicSettings.put("animal-spawn", true);
        basicSettings.put("block-break", false); // non-members break
        basicSettings.put("block-place", false); // non-members place
        basicSettings.put("chest-open", false);  // non-members chest open
        basicSettings.put("interaction", false); // non-members buttons/doors

        // Default premium settings
        premiumSettings.put("creeper-explosion", false); // false = creeper blocks not destroyed
        premiumSettings.put("enderman-grief", false);
        premiumSettings.put("fire-spread", false);
        premiumSettings.put("leaf-decay", true);
        premiumSettings.put("weather-lock", false); // lock to sun/clear
        premiumSettings.put("time-lock", false);    // false = regular day/night cycle
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

    public int getLevel(String upgradeType) {
        return levels.getOrDefault(upgradeType, 1);
    }

    public void setLevel(String upgradeType, int level) {
        levels.put(upgradeType, level);
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

    // Helper serialization
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
