package id.alvarennation.acidIsland.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;

public class SkullsHook {

    private static boolean skullsEnabled = false;
    private static boolean hdbEnabled = false;

    public static void setup() {
        skullsEnabled = Bukkit.getPluginManager().getPlugin("Skulls") != null;
        hdbEnabled = Bukkit.getPluginManager().getPlugin("HeadDatabase") != null;
    }

    public static ItemStack getSkull(String headIdOrName) {
        if (headIdOrName == null || headIdOrName.isEmpty()) return new ItemStack(Material.PLAYER_HEAD);

        // Check HeadDatabase (hdb:id)
        if (headIdOrName.startsWith("hdb:") && hdbEnabled) {
            try {
                String id = headIdOrName.substring(4);
                Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
                Object apiInstance = apiClass.getConstructor().newInstance();
                Method getItemMethod = apiClass.getMethod("getItemHead", String.class);
                ItemStack head = (ItemStack) getItemMethod.invoke(apiInstance, id);
                if (head != null) return head;
            } catch (Throwable ignored) {}
        }

        // Check Skulls plugin (skulls:id)
        if (headIdOrName.startsWith("skulls:") && skullsEnabled) {
            try {
                String id = headIdOrName.substring(7);
                Class<?> apiClass = Class.forName("ca.tweetzy.skulls.api.SkullsAPI");
                Method getItemMethod = apiClass.getMethod("getSkull", String.class);
                ItemStack head = (ItemStack) getItemMethod.invoke(null, id);
                if (head != null) return head;
            } catch (Throwable ignored) {}
        }

        // Standard player head by owner name
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(headIdOrName));
            head.setItemMeta(meta);
        }
        return head;
    }
}
