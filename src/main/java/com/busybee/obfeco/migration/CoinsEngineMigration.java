package com.busybee.obfeco.migration;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    private void listDatabaseTables(Connection connection) {
        try {
            String query = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name";
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                plugin.getLogger().info("Available tables in CoinsEngine database:");
                while (rs.next()) {
                    plugin.getLogger().info("  - " + rs.getString("name"));
                }
            }
        } catch (Exception e) {
            // Might be MySQL, try MySQL approach
            try {
                String query = "SHOW TABLES";
                try (PreparedStatement stmt = connection.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {
                    plugin.getLogger().info("Available tables in CoinsEngine database:");
                    while (rs.next()) {
                        plugin.getLogger().info("  - " + rs.getString(1));
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not list database tables: " + ex.getMessage());
            }
        }
    }

    private Connection getCoinsEngineConnection() throws Exception {
        File coinsEngineFolder = new File(plugin.getDataFolder().getParentFile(), "CoinsEngine");
        if (!coinsEngineFolder.exists()) {
            throw new Exception("CoinsEngine folder not found");
        }

        File configFile = new File(coinsEngineFolder, "config.yml");
        if (!configFile.exists()) {
            throw new Exception("CoinsEngine config.yml not found");
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Check for database configuration
        String storageType = config.getString("Database.Type", "SQLITE");

        if (storageType.equalsIgnoreCase("MYSQL")) {
            String host = config.getString("Database.MySQL.Host", "localhost");
            int port = config.getInt("Database.MySQL.Port", 3306);
            String database = config.getString("Database.MySQL.Database", "minecraft");
            String username = config.getString("Database.MySQL.Username", "root");
            String password = config.getString("Database.MySQL.Password", "");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
            plugin.getLogger().info("Connecting to CoinsEngine MySQL database...");
            return DriverManager.getConnection(url, username, password);
        } else {
            // Default to SQLite
            File dbFile = new File(coinsEngineFolder, "data.db");
            if (!dbFile.exists()) {
                throw new Exception("CoinsEngine database file not found: " + dbFile.getAbsolutePath());
            }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            plugin.getLogger().info("Connecting to CoinsEngine SQLite database: " + dbFile.getAbsolutePath());
            return DriverManager.getConnection(url);
        }
    }

    public void migrate(MigrationCallback callback) {
        if (!isCoinsEngineAvailable()) {
            callback.onComplete(false, 0, 0, "CoinsEngine plugin not found");
            return;
        }

        plugin.getLogger().info("=== CoinsEngine Migration Started ===");

        CompletableFuture.runAsync(() -> {
            Connection connection = null;
            try {
                // Get CoinsEngine currencies via API for metadata
                Collection<su.nightexpress.coinsengine.api.currency.Currency> ceCurrencies =
                    su.nightexpress.coinsengine.api.CoinsEngineAPI.getCurrencies();

                if (ceCurrencies == null || ceCurrencies.isEmpty()) {
                    plugin.getLogger().warning("No currencies found in CoinsEngine");
                    Bukkit.getScheduler().runTask(plugin, () ->
                        callback.onComplete(false, 0, 0, "No currencies found in CoinsEngine"));
                    return;
                }

                plugin.getLogger().info("Found " + ceCurrencies.size() + " currencies in CoinsEngine");

                // Create/verify Obfeco currencies
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

                Thread.sleep(200);

                // Connect to CoinsEngine database
                connection = getCoinsEngineConnection();
                plugin.getLogger().info("Connected to CoinsEngine database");

                // List available tables for debugging
                listDatabaseTables(connection);

                // Read all balances directly from database
                Map<String, Map<UUID, Double>> allBalances = new HashMap<>();
                for (su.nightexpress.coinsengine.api.currency.Currency ceCurrency : ceCurrencies) {
                    allBalances.put(ceCurrency.getId(), new HashMap<>());
                }

                Set<UUID> allPlayerIds = new HashSet<>();

                // CoinsEngine typically uses a wide-table format with one column per currency
                // Try to read from coinsengine_users table with dynamic columns
                boolean querySuccess = false;

                try {
                    // Get table metadata to find currency columns
                    java.sql.DatabaseMetaData metaData = connection.getMetaData();
                    ResultSet columns = metaData.getColumns(null, null, "coinsengine_users", null);

                    List<String> currencyColumns = new ArrayList<>();
                    String uuidColumn = null;

                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        // UUID column is typically 'uuid', 'player_uuid', or 'player_id'
                        if (columnName.equalsIgnoreCase("uuid") ||
                            columnName.equalsIgnoreCase("player_uuid") ||
                            columnName.equalsIgnoreCase("player_id")) {
                            uuidColumn = columnName;
                        } else if (!columnName.equalsIgnoreCase("id") &&
                                   !columnName.equalsIgnoreCase("name") &&
                                   !columnName.equalsIgnoreCase("player_name")) {
                            // Assume other columns are currency balances
                            currencyColumns.add(columnName);
                        }
                    }
                    columns.close();

                    if (uuidColumn != null && !currencyColumns.isEmpty()) {
                        plugin.getLogger().info("Found UUID column: " + uuidColumn);
                        plugin.getLogger().info("Found " + currencyColumns.size() + " potential currency columns");

                        // Build and execute query
                        StringBuilder queryBuilder = new StringBuilder("SELECT " + uuidColumn);
                        for (String col : currencyColumns) {
                            queryBuilder.append(", ").append(col);
                        }
                        queryBuilder.append(" FROM coinsengine_users");

                        String query = queryBuilder.toString();
                        plugin.getLogger().info("Querying CoinsEngine with: " + query);

                        try (PreparedStatement stmt = connection.prepareStatement(query);
                             ResultSet rs = stmt.executeQuery()) {

                            while (rs.next()) {
                                try {
                                    String playerIdStr = rs.getString(uuidColumn);
                                    UUID playerId = UUID.fromString(playerIdStr);
                                    allPlayerIds.add(playerId);

                                    // Read each currency balance
                                    for (String currencyCol : currencyColumns) {
                                        try {
                                            double balance = rs.getDouble(currencyCol);
                                            if (balance > 0 && allBalances.containsKey(currencyCol)) {
                                                allBalances.get(currencyCol).put(playerId, balance);
                                            }
                                        } catch (Exception e) {
                                            // Column might not be a currency or might be NULL
                                        }
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to parse balance record: " + e.getMessage());
                                }
                            }

                            plugin.getLogger().info("Loaded balances for " + allPlayerIds.size() + " players from database");
                            querySuccess = true;
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to query coinsengine_users table: " + e.getMessage());
                }

                if (!querySuccess) {
                    plugin.getLogger().warning("Could not read CoinsEngine balance data.");
                    plugin.getLogger().warning("Please check your CoinsEngine database structure.");
                }

                plugin.getLogger().info("Saving balances to database...");
                int currenciesMigrated = 0;
                int playersProcessed = allPlayerIds.size();
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
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                        plugin.getLogger().info("Closed CoinsEngine database connection");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
                    }
                }
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

    public CompletableFuture<List<CurrencyScanResult>> scanCurrenciesDetailed() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isCoinsEngineAvailable()) {
                return Collections.emptyList();
            }

            try {
                Collection<su.nightexpress.coinsengine.api.currency.Currency> currencies =
                    su.nightexpress.coinsengine.api.CoinsEngineAPI.getCurrencies();

                List<CurrencyScanResult> results = new ArrayList<>();
                for (su.nightexpress.coinsengine.api.currency.Currency currency : currencies) {
                    boolean exists = plugin.getCurrencyManager().getCurrency(currency.getId()) != null;
                    CurrencyScanResult result = new CurrencyScanResult(
                        currency.getId(),
                        currency.getName(),
                        currency.getSymbol(),
                        currency.getStartValue(),
                        currency.isDecimal(),
                        exists
                    );
                    results.add(result);
                }
                return results;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to scan currencies: " + e.getMessage());
                return Collections.emptyList();
            }
        });
    }
}