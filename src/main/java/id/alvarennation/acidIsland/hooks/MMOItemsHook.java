package id.alvarennation.acidIsland.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class MMOItemsHook {

    private static boolean enabled = false;

    public static void setup() {
        if (Bukkit.getPluginManager().getPlugin("MMOItems") != null) {
            enabled = true;
        }
    }

    public static ItemStack getItem(String itemStr) {
        if (itemStr == null || itemStr.isEmpty()) return null;

        if (itemStr.startsWith("MMOITEMS:") && enabled) {
            try {
                String[] split = itemStr.split(":");
                if (split.length >= 3) {
                    String typeStr = split[1];
                    String idStr = split[2];
                    int amount = split.length > 3 ? Integer.parseInt(split[3]) : 1;

                    Class<?> mmoItemsClass = Class.forName("net.Indyce.mmoitems.MMOItems");
                    Object pluginInstance = mmoItemsClass.getField("plugin").get(null);
                    Method getItemMethod = pluginInstance.getClass().getMethod("getItem", String.class, String.class);
                    ItemStack item = (ItemStack) getItemMethod.invoke(pluginInstance, typeStr, idStr);
                    if (item != null) {
                        item.setAmount(amount);
                        return item;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Fallback ke Material standar
        String[] split = itemStr.split(":");
        Material mat = Material.matchMaterial(split[0]);
        int amount = split.length > 1 ? Integer.parseInt(split[1]) : 1;
        if (mat != null) {
            return new ItemStack(mat, amount);
        }

        return null;
    }
}
