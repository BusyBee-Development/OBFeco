package net.busybee.obfeco.hooks;

import org.bukkit.OfflinePlayer;

public class PAPIUtil {
    public static String setPlaceholders(OfflinePlayer player, String text) {
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (NoClassDefFoundError | Exception e) {
            return text;
        }
    }
}
