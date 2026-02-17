package com.busybee.obfeco.migration;

import com.busybee.obfeco.Obfeco;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class MigrationManager {
    private final Obfeco plugin;

    public interface MigrationCallback {
        void onComplete(boolean success, int count, String error);
    }

    public void migrate(String source, String targetCurrency, MigrationCallback callback) {
        if (source.equalsIgnoreCase("coinsengine")) {
            migrateCoinsEngine(targetCurrency, callback);
        } else if (source.equalsIgnoreCase("vault")) {
            migrateVault(targetCurrency, callback);
        } else {
            callback.onComplete(false, 0, "Unsupported source: " + source);
        }
    }

    private void migrateCoinsEngine(String targetCurrency, MigrationCallback callback) {
        plugin.getLogger().info("=== CoinEngine Migration Started ===");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String migrationType = plugin.getConfigManager().getMigrationType();
                String configTable = plugin.getConfigManager().getMigrationTable();
                plugin.getLogger().info("Migration type: " + migrationType);
                plugin.getLogger().info("Configured table: " + configTable);

                // Build connection URL
                final String url;
                if (migrationType.equals("sqlite")) {
                    String file = plugin.getConfigManager().getMigrationFile();
                    java.io.File dbFile = new java.io.File(file);
                    if (!dbFile.isAbsolute()) {
                        dbFile = new java.io.File(plugin.getDataFolder().getParentFile().getParentFile(), file);
                    }
                    final String dbPath = dbFile.getAbsolutePath();
                    plugin.getLogger().info("Database file path: " + dbPath);
                    plugin.getLogger().info("File exists: " + dbFile.exists());
                    plugin.getLogger().info("File size: " + (dbFile.exists() ? dbFile.length() + " bytes" : "N/A"));

                    if (!dbFile.exists()) {
                        String errorMsg = "SQLite database file not found: " + dbPath;
                        plugin.getLogger().severe(errorMsg);
                        Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(false, 0, errorMsg));
                        return;
                    }
                    url = "jdbc:sqlite:" + dbPath;
                } else {
                    String host = plugin.getConfig().getString("migration.coinsengine.host", "localhost");
                    int port = plugin.getConfig().getInt("migration.coinsengine.port", 3306);
                    String database = plugin.getConfig().getString("migration.coinsengine.database", "minecraft");
                    url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
                    plugin.getLogger().info("MySQL connection: " + host + ":" + port + "/" + database);
                }

                // Get currency mappings
                Map<String, String> currencyMappings = plugin.getConfigManager().getMigrationMappings();
                plugin.getLogger().info("Currency mappings: " + currencyMappings);

                if (currencyMappings.isEmpty()) {
                    String errorMsg = "No currency mappings configured. Check migration.coinsengine.mappings in config.yml";
                    plugin.getLogger().severe(errorMsg);
                    Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(false, 0, errorMsg));
                    return;
                }

                // Connect to database
                plugin.getLogger().info("Establishing database connection...");
                Connection conn;
                if (migrationType.equals("sqlite")) {
                    conn = DriverManager.getConnection(url);
                } else {
                    String user = plugin.getConfig().getString("migration.coinsengine.username", "root");
                    String pass = plugin.getConfig().getString("migration.coinsengine.password", "password");
                    conn = DriverManager.getConnection(url, user, pass);
                }
                plugin.getLogger().info("Database connection established!");

                int totalMigrated = 0;
                int currenciesProcessed = 0;
                StringBuilder resultMessage = new StringBuilder();

                try (conn) {
                    // Auto-detect table if configured table doesn't exist
                    plugin.getLogger().info("Detecting database tables...");
                    String actualTable = configTable;
                    try (ResultSet tables = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                        plugin.getLogger().info("Available tables:");
                        boolean foundConfigTable = false;
                        while (tables.next()) {
                            String tableName = tables.getString("TABLE_NAME");
                            plugin.getLogger().info("  - " + tableName);
                            if (tableName.equalsIgnoreCase(configTable)) {
                                foundConfigTable = true;
                            }
                            // Auto-detect CoinEngine table
                            if (!foundConfigTable && (tableName.toLowerCase().contains("coin") || tableName.toLowerCase().contains("user"))) {
                                actualTable = tableName;
                            }
                        }
                        if (!foundConfigTable && !actualTable.equals(configTable)) {
                            plugin.getLogger().warning("Configured table '" + configTable + "' not found. Using detected table: " + actualTable);
                        }
                    }

                    plugin.getLogger().info("Using table: " + actualTable);

                    // Detect table structure
                    plugin.getLogger().info("Analyzing table structure...");
                    ResultSetMetaData meta;
                    String uuidCol = "uuid";
                    Map<String, String> balanceColumns = new HashMap<>();

                    // System columns to skip when auto-detecting currencies
                    java.util.Set<String> systemColumns = new java.util.HashSet<>(
                        java.util.Arrays.asList("id", "uuid", "player_uuid", "name", "datecreated",
                            "last_online", "lastonline", "settings", "hiddenfromtops", "currencydata")
                    );

                    try (PreparedStatement checkStmt = conn.prepareStatement("SELECT * FROM " + actualTable + " LIMIT 1");
                         ResultSet checkRs = checkStmt.executeQuery()) {
                        meta = checkRs.getMetaData();
                        int colCount = meta.getColumnCount();
                        plugin.getLogger().info("Table has " + colCount + " columns:");

                        // Log all columns
                        for (int i = 1; i <= colCount; i++) {
                            String colName = meta.getColumnName(i);
                            String colType = meta.getColumnTypeName(i);
                            plugin.getLogger().info("  " + i + ". " + colName + " (" + colType + ")");

                            String colNameLower = colName.toLowerCase();

                            // Detect UUID column
                            if (colNameLower.equals("player_uuid")) {
                                uuidCol = colName;
                            }

                            // Detect balance columns - support multiple formats:
                            // 1. Old format: balance_coins, balance_gems
                            if (colNameLower.startsWith("balance_")) {
                                String currencyName = colNameLower.substring(8);
                                balanceColumns.put(currencyName, colName);
                                plugin.getLogger().info("     → Detected currency (balance_ prefix): " + currencyName);
                            }
                            // 2. New format: coins, gems, money (direct column names)
                            // Check if column name matches any currency in our mappings
                            else if (currencyMappings.containsKey(colNameLower)) {
                                balanceColumns.put(colNameLower, colName);
                                plugin.getLogger().info("     → Detected currency (mapped): " + colNameLower);
                            }
                            // 3. Auto-detect: numeric columns that aren't system columns
                            else if (!systemColumns.contains(colNameLower) &&
                                     (colType.equalsIgnoreCase("REAL") ||
                                      colType.equalsIgnoreCase("DOUBLE") ||
                                      colType.equalsIgnoreCase("FLOAT") ||
                                      colType.equalsIgnoreCase("DECIMAL"))) {
                                balanceColumns.put(colNameLower, colName);
                                plugin.getLogger().warning("     → Auto-detected possible currency: " + colNameLower + " (not in config mappings)");
                            }
                        }

                        plugin.getLogger().info("UUID column: " + uuidCol);
                        plugin.getLogger().info("Balance columns found: " + balanceColumns.keySet());
                    } catch (Exception e) {
                        String errorMsg = "Failed to analyze table structure: " + e.getMessage();
                        plugin.getLogger().severe(errorMsg);
                        e.printStackTrace();
                        Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(false, 0, errorMsg));
                        return;
                    }

                    if (balanceColumns.isEmpty()) {
                        String errorMsg = "No balance_* columns found in table '" + actualTable + "'. This doesn't appear to be a CoinEngine database.";
                        plugin.getLogger().severe(errorMsg);
                        Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(false, 0, errorMsg));
                        return;
                    }

                    // Migrate each currency
                    plugin.getLogger().info("Starting currency migration...");
                    for (Map.Entry<String, String> mapping : currencyMappings.entrySet()) {
                        String sourceCurrency = mapping.getKey();
                        String obfecoCurrency = mapping.getValue();
                        plugin.getLogger().info("Processing: " + sourceCurrency + " -> " + obfecoCurrency);

                        String balanceColumn = balanceColumns.get(sourceCurrency);
                        if (balanceColumn == null) {
                            plugin.getLogger().warning("Currency '" + sourceCurrency + "' not found in database. Available: " + balanceColumns.keySet());
                            resultMessage.append("\n- ").append(sourceCurrency).append(": NOT FOUND");
                            continue;
                        }

                        Map<UUID, Double> data = new HashMap<>();
                        String query = "SELECT " + uuidCol + ", " + balanceColumn + " FROM " + actualTable;
                        plugin.getLogger().info("Query: " + query);

                        try (PreparedStatement stmt = conn.prepareStatement(query);
                             ResultSet rs = stmt.executeQuery()) {

                            int rowCount = 0;
                            while (rs.next()) {
                                rowCount++;
                                try {
                                    String uuidStr = rs.getString(uuidCol);
                                    double balance = rs.getDouble(balanceColumn);

                                    if (balance > 0) {
                                        UUID uuid = UUID.fromString(uuidStr);
                                        data.put(uuid, balance);
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Skipping invalid row: " + e.getMessage());
                                }
                            }
                            plugin.getLogger().info("Scanned " + rowCount + " rows, found " + data.size() + " valid balances");
                        }

                        if (!data.isEmpty()) {
                            plugin.getLogger().info("Creating currency table: " + obfecoCurrency);
                            plugin.getDatabaseManager().createCurrencyTable(obfecoCurrency);
                            plugin.getLogger().info("Batch writing " + data.size() + " balances...");
                            plugin.getDatabaseManager().batchSetBalances(obfecoCurrency, data);
                            totalMigrated += data.size();
                            currenciesProcessed++;
                            plugin.getLogger().info("✓ Successfully migrated " + data.size() + " balances for " + sourceCurrency);
                            resultMessage.append("\n- ").append(sourceCurrency).append(" -> ").append(obfecoCurrency).append(": ").append(data.size()).append(" players");
                        } else {
                            plugin.getLogger().info("No data found for currency: " + sourceCurrency);
                            resultMessage.append("\n- ").append(sourceCurrency).append(": 0 players");
                        }
                    }
                }

                plugin.getLogger().info("=== Migration Complete ===");
                plugin.getLogger().info("Currencies processed: " + currenciesProcessed);
                plugin.getLogger().info("Total balances migrated: " + totalMigrated);

                if (currenciesProcessed > 0) {
                    String finalMessage = "Migrated " + currenciesProcessed + " currencies (" + totalMigrated + " total balances)" + resultMessage.toString();
                    int finalTotal = totalMigrated;
                    Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(true, finalTotal, finalMessage));
                } else {
                    String errorMsg = "No currencies were migrated. Check your mappings and table structure." + resultMessage.toString();
                    plugin.getLogger().warning(errorMsg);
                    Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(false, 0, errorMsg));
                }

            } catch (Exception e) {
                plugin.getLogger().severe("=== Migration Failed ===");
                plugin.getLogger().severe("Error: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> callback.onComplete(false, 0, "Error: " + e.getMessage()));
            }
        });
    }

    private void migrateVault(String targetCurrency, MigrationCallback callback) {
        callback.onComplete(false, 0, "Vault migration not yet implemented");
    }
}