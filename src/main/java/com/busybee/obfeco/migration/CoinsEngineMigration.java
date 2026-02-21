package com.busybee.obfeco.migration;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class CoinsEngineMigration {
    private final Obfeco plugin;

    public interface MigrationCallback {
        void onComplete(boolean success, int playersProcessed, int currenciesMigrated, String details);
    }

    public boolean isCoinsEngineAvailable() {
        return Bukkit.getPluginManager().getPlugin("CoinsEngine") != null;
    }

    public void migrate(MigrationCallback callback) {
        if (!isCoinsEngineAvailable()) {
            callback.onComplete(false, 0, 0, "CoinsEngine plugin not found");
            return;
        }

        plugin.getLogger().info("=== CoinsEngine Migration Started ===");

        CompletableFuture.runAsync(() -> {
            try {
                Collection<su.nightexpress.coinsengine.api.currency.Currency> ceCurrencies =
                    su.nightexpress.coinsengine.api.CoinsEngineAPI.getCurrencies();

                if (ceCurrencies == null || ceCurrencies.isEmpty()) {
                    plugin.getLogger().warning("No currencies found in CoinsEngine");
                    Bukkit.getScheduler().runTask(plugin, () ->
                        callback.onComplete(false, 0, 0, "No currencies found in CoinsEngine"));
                    return;
                }

                plugin.getLogger().info("Found " + ceCurrencies.size() + " currencies in CoinsEngine");

                Map<String, Currency> obfecoCurrencies = new HashMap<>();
                for (su.nightexpress.coinsengine.api.currency.Currency ceCurrency : ceCurrencies) {
                    String currencyId = ceCurrency.getId();
                    Currency obfecoCurrency = plugin.getCurrencyManager().getCurrency(currencyId);

                    if (obfecoCurrency == null) {
                        Currency newCurrency = new Currency(
                            currencyId,
                            ceCurrency.getName(),
                            ceCurrency.getSymbol(),
                            "%amount%%symbol%",
                            "GOLD_INGOT",
                            ceCurrency.getStartValue(),
                            ceCurrency.isDecimal(),
                            true,
                            true
                        );

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getCurrencyManager().addCurrency(newCurrency);
                            plugin.getDatabaseManager().createCurrencyTable(currencyId);
                        });

                        obfecoCurrencies.put(currencyId, newCurrency);
                        plugin.getLogger().info("Created new Obfeco currency: " + currencyId);
                    } else {
                        obfecoCurrencies.put(currencyId, obfecoCurrency);
                        plugin.getLogger().info("Using existing Obfeco currency: " + currencyId);
                    }
                }

                Thread.sleep(100);

                OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();
                plugin.getLogger().info("Scanning " + allPlayers.length + " players...");

                int playersProcessed = 0;
                Map<String, Map<UUID, Double>> allBalances = new HashMap<>();

                for (su.nightexpress.coinsengine.api.currency.Currency ceCurrency : ceCurrencies) {
                    allBalances.put(ceCurrency.getId(), new HashMap<>());
                }

                for (OfflinePlayer player : allPlayers) {
                    UUID playerId = player.getUniqueId();
                    boolean hasNonDefaultBalance = false;

                    for (su.nightexpress.coinsengine.api.currency.Currency ceCurrency : ceCurrencies) {
                        try {
                            double balance = su.nightexpress.coinsengine.api.CoinsEngineAPI.getBalance(playerId, ceCurrency);
                            double defaultBalance = ceCurrency.getStartValue();

                            if (balance != defaultBalance && balance > 0) {
                                allBalances.get(ceCurrency.getId()).put(playerId, balance);
                                hasNonDefaultBalance = true;
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to get balance for " + playerId + " in " + ceCurrency.getId() + ": " + e.getMessage());
                        }
                    }

                    if (hasNonDefaultBalance) {
                        playersProcessed++;

                        if (playersProcessed % 100 == 0) {
                            plugin.getLogger().info("Processed " + playersProcessed + " players...");
                        }
                    }
                }

                plugin.getLogger().info("Saving balances to database...");
                int currenciesMigrated = 0;
                StringBuilder details = new StringBuilder();

                for (Map.Entry<String, Map<UUID, Double>> entry : allBalances.entrySet()) {
                    String currencyId = entry.getKey();
                    Map<UUID, Double> balances = entry.getValue();

                    if (!balances.isEmpty()) {
                        plugin.getDatabaseManager().batchSetBalances(currencyId, balances);
                        currenciesMigrated++;
                        plugin.getLogger().info("Migrated " + balances.size() + " balances for " + currencyId);
                        details.append("\n- ").append(currencyId).append(": ").append(balances.size()).append(" players");
                    }
                }

                plugin.getLogger().info("=== Migration Complete ===");
                plugin.getLogger().info("Players processed: " + playersProcessed);
                plugin.getLogger().info("Currencies migrated: " + currenciesMigrated);

                int finalPlayersProcessed = playersProcessed;
                int finalCurrenciesMigrated = currenciesMigrated;
                String finalDetails = details.toString();

                Bukkit.getScheduler().runTask(plugin, () ->
                    callback.onComplete(true, finalPlayersProcessed, finalCurrenciesMigrated, finalDetails));

            } catch (Exception e) {
                plugin.getLogger().severe("Migration failed: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                    callback.onComplete(false, 0, 0, "Error: " + e.getMessage()));
            }
        });
    }

    public CompletableFuture<List<String>> scanCurrencies() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isCoinsEngineAvailable()) {
                return Collections.emptyList();
            }

            try {
                Collection<su.nightexpress.coinsengine.api.currency.Currency> currencies =
                    su.nightexpress.coinsengine.api.CoinsEngineAPI.getCurrencies();

                List<String> currencyIds = new ArrayList<>();
                for (su.nightexpress.coinsengine.api.currency.Currency currency : currencies) {
                    currencyIds.add(currency.getId());
                }
                return currencyIds;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to scan currencies: " + e.getMessage());
                return Collections.emptyList();
            }
        });
    }
}