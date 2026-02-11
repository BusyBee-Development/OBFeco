package com.busybee.obfeco.storage;

import com.busybee.obfeco.Obfeco;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class YamlStorageManager {
    private final Obfeco plugin;

    private File getDataFolder() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    private File getCurrencyFile(String currencyId) {
        return new File(getDataFolder(), currencyId.toLowerCase() + ".yml");
    }

    public double getBalance(UUID playerId, String currencyId) {
        File file = getCurrencyFile(currencyId);
        if (!file.exists()) {
            return plugin.getCurrencyManager().getCurrency(currencyId).getStartingBalance();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getDouble(playerId.toString(), plugin.getCurrencyManager().getCurrency(currencyId).getStartingBalance());
    }

    public void setBalance(UUID playerId, String currencyId, double balance) {
        File file = getCurrencyFile(currencyId);
        YamlConfiguration config;

        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            config = new YamlConfiguration();
        }

        config.set(playerId.toString(), balance);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save balance for " + playerId + " in currency " + currencyId + ": " + e.getMessage());
        }
    }

    public void batchSetBalances(String currencyId, Map<UUID, Double> balances) {
        if (balances.isEmpty()) return;

        File file = getCurrencyFile(currencyId);
        YamlConfiguration config;

        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            config = new YamlConfiguration();
        }

        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to batch save balances for currency " + currencyId + ": " + e.getMessage());
        }
    }

    public List<Map.Entry<UUID, Double>> getTopBalances(String currencyId, int limit) {
        File file = getCurrencyFile(currencyId);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<UUID, Double> balances = new HashMap<>();

        for (String key : config.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                double balance = config.getDouble(key);
                balances.put(playerId, balance);
            } catch (IllegalArgumentException ignored) {
                // Skip invalid UUIDs
            }
        }

        return balances.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    public boolean resetCurrency(String currencyId) {
        File file = getCurrencyFile(currencyId);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    public double getTotalCurrencyValue(String currencyId) {
        File file = getCurrencyFile(currencyId);
        if (!file.exists()) {
            return 0.0;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        double total = 0.0;

        for (String key : config.getKeys(false)) {
            total += config.getDouble(key, 0.0);
        }

        return total;
    }

    public boolean createCurrencyTable(String currencyId) {
        // For YAML storage, we don't need to pre-create files
        // They'll be created when the first balance is set
        return true;
    }

    public boolean deleteCurrencyTable(String currencyId) {
        return resetCurrency(currencyId);
    }
}
