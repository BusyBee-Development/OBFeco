package net.busybee.obfeco.util;

import net.busybee.obfeco.Obfeco;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

@RequiredArgsConstructor
public class LogManager {
    private final Obfeco plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void logTransaction(String actor, String target, String currency, String action, double amount) {
        if (!plugin.getConfigManager().isLoggingEnabled() || !plugin.getConfigManager().isFileEnabled()) return;

        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            if (!logsDir.mkdirs()) {
                plugin.getLogger().warning("Failed to create logs directory at: " + logsDir.getAbsolutePath());
                return;
            }
        }

        String fileName = "obfeco-" + dateFormat.format(new Date()) + ".log";
        File logFile = new File(logsDir, fileName);
        String timestamp = timeFormat.format(new Date());
        String formattedAmount = String.valueOf(amount);
        String message = String.format("[%s] %s %s %s %s to %s", timestamp, actor, action, formattedAmount, currency, target);

        FoliaUtil.runAsync(plugin, () -> {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(message);
                pw.flush();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write to log file: " + e.getMessage());
            }
        });
    }
}
