package com.busybee.obfeco.database;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import com.busybee.obfeco.storage.YamlStorageManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@RequiredArgsConstructor
public class DatabaseManager {
    private final Obfeco plugin;
    private HikariDataSource dataSource;
    private YamlStorageManager yamlStorage;

    public boolean initialize() {
        String storageType = plugin.getConfigManager().getStorageType();

        if (storageType.equals("YAML")) {
            this.yamlStorage = new YamlStorageManager(plugin);
            plugin.getLogger().info("Using YAML storage for player balances");
            return true;
        }

        try {
            HikariConfig config = new HikariConfig();

            if (storageType.equals("SQLITE")) {
                config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/data.db");
                config.setDriverClassName("org.sqlite.JDBC");
            } else if (storageType.equals("MYSQL")) {
                config.setJdbcUrl("jdbc:mysql://" + plugin.getConfigManager().getDatabaseHost() + ":" +
                    plugin.getConfigManager().getDatabasePort() + "/" + plugin.getConfigManager().getDatabaseName() +
                    "?useSSL=false&allowPublicKeyRetrieval=true");
                config.setUsername(plugin.getConfigManager().getDatabaseUsername());
                config.setPassword(plugin.getConfigManager().getDatabasePassword());
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            } else {
                plugin.getLogger().severe("Invalid storage type: " + storageType + ". Use YAML, SQLITE, or MYSQL");
                return false;
            }

            config.setMaximumPoolSize(plugin.getConfigManager().getDatabasePoolSize());
            config.setConnectionTimeout(plugin.getConfigManager().getDatabaseConnectionTimeout());
            config.setMaxLifetime(plugin.getConfigManager().getDatabaseMaxLifetime());

            this.dataSource = new HikariDataSource(config);

            plugin.getLogger().info("Database connection established (" + storageType + ")");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database is not initialized (using YAML storage)");
        }
        return dataSource.getConnection();
    }

    public boolean createCurrencyTable(String currencyId) {
        if (yamlStorage != null) {
            return yamlStorage.createCurrencyTable(currencyId);
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();

        String createTable = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
            "player_uuid VARCHAR(36) PRIMARY KEY, " +
            "balance DOUBLE NOT NULL DEFAULT 0.0, " +
            "last_updated BIGINT NOT NULL" +
            ")";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(createTable)) {
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create table for currency " + currencyId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteCurrencyTable(String currencyId) {
        if (yamlStorage != null) {
            return yamlStorage.deleteCurrencyTable(currencyId);
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();
        String dropTable = "DROP TABLE IF EXISTS " + tableName;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(dropTable)) {
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete table for currency " + currencyId + ": " + e.getMessage());
            return false;
        }
    }

    public double getBalance(UUID playerId, String currencyId) {
        if (yamlStorage != null) {
            return yamlStorage.getBalance(playerId, currencyId);
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();
        String query = "SELECT balance FROM " + tableName + " WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, playerId.toString());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get balance for " + playerId + " in currency " + currencyId + ": " + e.getMessage());
        }

        Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
        return currency != null ? currency.getStartingBalance() : 0.0;
    }

    private boolean isSQLite() {
        try (Connection conn = getConnection()) {
            return conn.getMetaData().getURL().startsWith("jdbc:sqlite");
        } catch (SQLException e) {
            return false;
        }
    }

    private String buildUpsert(String tableName, boolean sqlite) {
        if (sqlite) {
            return "INSERT OR REPLACE INTO " + tableName + " (player_uuid, balance, last_updated) VALUES (?, ?, ?)";
        }
        return "INSERT INTO " + tableName + " (player_uuid, balance, last_updated) VALUES (?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE balance = VALUES(balance), last_updated = VALUES(last_updated)";
    }

    public void setBalance(UUID playerId, String currencyId, double balance) {
        if (yamlStorage != null) {
            yamlStorage.setBalance(playerId, currencyId, balance);
            return;
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();
        boolean sqlite = isSQLite();
        String upsert = buildUpsert(tableName, sqlite);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsert)) {
            long timestamp = System.currentTimeMillis();
            stmt.setString(1, playerId.toString());
            stmt.setDouble(2, balance);
            stmt.setLong(3, timestamp);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to set balance for " + playerId + " in currency " + currencyId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void batchSetBalances(String currencyId, Map<UUID, Double> balances) {
        if (balances.isEmpty()) return;

        if (yamlStorage != null) {
            yamlStorage.batchSetBalances(currencyId, balances);
            return;
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();
        boolean sqlite = isSQLite();
        String upsert = buildUpsert(tableName, sqlite);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsert)) {
            conn.setAutoCommit(false);
            long timestamp = System.currentTimeMillis();

            for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
                stmt.setString(1, entry.getKey().toString());
                stmt.setDouble(2, entry.getValue());
                stmt.setLong(3, timestamp);
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
            plugin.getLogger().info("[DB] Wrote " + balances.size() + " balances for currency: " + currencyId);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to batch save balances for currency " + currencyId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Map.Entry<UUID, Double>> getTopBalances(String currencyId, int limit) {
        if (yamlStorage != null) {
            return yamlStorage.getTopBalances(currencyId, limit);
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();
        List<Map.Entry<UUID, Double>> topBalances = new ArrayList<>();

        String query = "SELECT player_uuid, balance FROM " + tableName + " ORDER BY balance DESC LIMIT ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, limit);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                double balance = rs.getDouble("balance");
                topBalances.add(new AbstractMap.SimpleEntry<>(playerId, balance));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get top balances for currency " + currencyId + ": " + e.getMessage());
        }

        return topBalances;
    }

    public boolean resetCurrency(String currencyId) {
        if (yamlStorage != null) {
            return yamlStorage.resetCurrency(currencyId);
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();
        String truncate = "DELETE FROM " + tableName;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(truncate)) {
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to reset currency " + currencyId + ": " + e.getMessage());
            return false;
        }
    }

    public double getTotalCurrencyValue(String currencyId) {
        if (yamlStorage != null) {
            return yamlStorage.getTotalCurrencyValue(currencyId);
        }

        String tableName = "obfeco_" + currencyId.toLowerCase();
        String query = "SELECT SUM(balance) as total FROM " + tableName;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get total value for currency " + currencyId + ": " + e.getMessage());
        }

        return 0.0;
    }
}