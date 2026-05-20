package net.busybee.obfeco.util;

import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.ErrorTracker;
import dev.faststats.core.data.Metric;
import net.busybee.obfeco.Obfeco;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class FastStatsManager {
    private final Obfeco plugin;
    private final BukkitMetrics metrics;

    public static final ErrorTracker ERROR_TRACKER = ErrorTracker.contextAware()
            .anonymize("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "[uuid hidden]")
            .ignoreError(java.lang.reflect.InvocationTargetException.class);

    public FastStatsManager(Obfeco plugin) {
        this.plugin = plugin;
        String token = loadToken();

        this.metrics = BukkitMetrics.factory()
                .token(token)
                .errorTracker(ERROR_TRACKER)
                .addMetric(Metric.number("server_tps", () -> {
                    try {
                        return Bukkit.getServer().getTPS()[0];
                    } catch (Exception e) {
                        return 20.0;
                    }
                }))
                .addMetric(Metric.number("currencies_total", () -> {
                    if (plugin.getCurrencyManager() != null && plugin.getCurrencyManager().getCurrencies() != null) {
                        return plugin.getCurrencyManager().getCurrencies().size();
                    }
                    return 0;
                }))
                .create(plugin);
    }

    private String loadToken() {
        Properties props = new Properties();
        try (InputStream is = plugin.getResource("faststats.properties")) {
            if (is != null) {
                props.load(is);
                return props.getProperty("token", "YOUR_TOKEN_HERE");
            }
        } catch (IOException ignored) {}
        return "YOUR_TOKEN_HERE";
    }

    public void onEnable() {
        metrics.ready();
        plugin.getLogger().info("FastStats metrics have been enabled!");
    }

    public void onDisable() {
        metrics.shutdown();
    }
}
