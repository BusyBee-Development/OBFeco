package com.busybee.obfeco.util;

import com.busybee.obfeco.hooks.PAPIUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

public class ColorUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    
    public static Component colorize(String text) {
        return colorize(null, text);
    }
    
    public static Component colorize(CommandSender sender, String text) {
        if (text == null) return null;
        String processed = text;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            processed = PAPIUtil.setPlaceholders(sender instanceof OfflinePlayer ? (OfflinePlayer) sender : null, processed);
        }
        return MINI_MESSAGE.deserialize(processed);
    }
    
    public static String colorizeToLegacy(String text) {
        return colorizeToLegacy((CommandSender) null, text);
    }
    
    public static String colorizeToLegacy(CommandSender sender, String text) {
        if (text == null) return null;
        String processed = text;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            processed = PAPIUtil.setPlaceholders(sender instanceof OfflinePlayer ? (OfflinePlayer) sender : null, processed);
        }
        Component component = MINI_MESSAGE.deserialize(processed);
        return LEGACY_SERIALIZER.serialize(component);
    }
    
    public static String strip(String text) {
        return MiniMessage.miniMessage().stripTags(text);
    }
}
