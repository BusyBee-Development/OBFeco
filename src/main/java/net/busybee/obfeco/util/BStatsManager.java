package net.busybee.obfeco.util;

import net.busybee.obfeco.Obfeco;
import org.bstats.bukkit.Metrics;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BStatsManager {
    public BStatsManager(Obfeco plugin) {
        int id = loadId(plugin);
        if (id != -1) {
            new Metrics(plugin, id);
        }
    }

    private int loadId(Obfeco plugin) {
        Properties props = new Properties();
        try (InputStream is = plugin.getResource("bstats.properties")) {
            if (is != null) {
                props.load(is);
                String idStr = props.getProperty("id");
                if (idStr != null) {
                    return Integer.parseInt(idStr.trim());
                }
            }
        } catch (IOException | NumberFormatException ignored) {}
        return -1;
    }
}
