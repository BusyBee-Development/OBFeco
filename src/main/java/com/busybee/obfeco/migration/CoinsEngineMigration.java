package com.busybee.obfeco.migration;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class CoinsEngineMigration {
    private final Obfeco plugin;

    private static final String USER_TABLE = "coinsengine_users";
    private static final String UUID_COL = "uuid";
    private static final String DATA_COL = "currencyData";

    private static final Set<String> SYSTEM_COLUMNS = new HashSet<>(Arrays.asList(
        "id", "uuid", "name", "datecreated", "last_online", "settings", "hiddenfromtops"
    ));

    public interface MigrationCallback {
        void onComplete(boolean success, int playersProcessed, int currenciesMigrated, String details);
    }

    public boolean isCoinsEngineAvailable() {
        return new File(plugin.getDataFolder().getParentFile(), "CoinsEngine").exists();
    }

    private CEConfig readConfig() throws Exception {
        File folder = new File(plugin.getDataFolder().getParentFile(), "CoinsEngine");
        File configFile = new File(folder, "config.yml");
        if (!configFile.exists()) throw new Exception("CoinsEngine config.yml not found at: " + configFile.getAbsolutePath());

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        String type = cfg.getString("Database.Type", "SQLITE").toUpperCase();

        if (type.equals("MYSQL")) {
            String host = cfg.getString("Database.MySQL.Host", "localhost");
            int port = cfg.getInt("Database.MySQL.Port", 3306);
            String database = cfg.getString("Database.MySQL.Database", "minecraft");
            String user = cfg.getString("Database.MySQL.Username", "root");
            String pass = cfg.getString("Database.MySQL.Password", "");
            return new CEConfig(type, host, port, database, user, pass, null);
        }

        File db = new File(folder, "data.db");
        if (!db.exists()) db = new File(folder, "coinsengine.db");
        if (!db.exists()) {
            File[] dbFiles = folder.listFiles((d, n) -> n.endsWith(".db"));
            if (dbFiles != null && dbFiles.length > 0) db = dbFiles[0];
        }
        if (!db.exists()) throw new Exception("CoinsEngine SQLite database not found in: " + folder.getAbsolutePath());
        return new CEConfig("SQLITE", null, 0, null, null, null, db);
    }

    private Connection openConnection(CEConfig cfg) throws SQLException {
        if (cfg.type.equals("MYSQL")) {
            String url = "jdbc:mysql://" + cfg.host + ":" + cfg.port + "/" + cfg.database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
            return DriverManager.getConnection(url, cfg.user, cfg.pass);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + cfg.dbFile.getAbsolutePath());
    }

    private Map<String, CECurrencyMeta> loadCurrencyMeta() {
        Map<String, CECurrencyMeta> map = new LinkedHashMap<>();

        if (CoinsEngineAPI.isLoaded()) {
            for (su.nightexpress.coinsengine.api.currency.Currency c : CoinsEngineAPI.getCurrencies()) {
                map.put(c.getId(), new CECurrencyMeta(
                    c.getId(), c.getName(), c.getSymbol(), c.getStartValue(), !c.isInteger()));
            }
            plugin.getLogger().info("[Migration] Loaded " + map.size() + " currencies via CoinsEngineAPI");
            return map;
        }

        File currenciesDir = new File(plugin.getDataFolder().getParentFile(), "CoinsEngine/currencies");
        if (currenciesDir.exists() && currenciesDir.isDirectory()) {
            File[] files = currenciesDir.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        FileConfiguration fc = YamlConfiguration.loadConfiguration(f);
                        String id = f.getName().replace(".yml", "");
                        String name = fc.getString("Name", id);
                        String symbol = fc.getString("Symbol", "");
                        double start = fc.getDouble("Start_Value", 0.0);
                        boolean decimals = fc.getBoolean("Decimals_Allowed", true);
                        map.put(id, new CECurrencyMeta(id, name, symbol, start, decimals));
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Migration] Could not read currency file: " + f.getName());
                    }
                }
            }
        }
        plugin.getLogger().info("[Migration] Loaded " + map.size() + " currencies from YML files");
        return map;
    }

    private List<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        } catch (SQLException ignored) {}

        if (columns.isEmpty()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet cols = meta.getColumns(null, null, tableName, null)) {
                while (cols.next()) columns.add(cols.getString("COLUMN_NAME"));
            }
            if (columns.isEmpty()) {
                try (ResultSet cols = meta.getColumns(null, null, tableName.toLowerCase(), null)) {
                    while (cols.next()) columns.add(cols.getString("COLUMN_NAME"));
                }
            }
        }

        return columns;
    }

    private boolean hasJsonDataColumn(Connection conn, String tableName) throws SQLException {
        List<String> columns = getTableColumns(conn, tableName);
        plugin.getLogger().info("[Migration] Columns in " + tableName + ": " + columns);
        for (String col : columns) {
            if (col.equalsIgnoreCase(DATA_COL)) {
                plugin.getLogger().info("[Migration] Found JSON data column: " + col);
                return true;
            }
        }
        return false;
    }

    private Map<String, Map<UUID, Double>> extractBalances(Connection conn, Set<String> knownCurrencies) throws SQLException {
        String actualTable = resolveTable(conn);
        plugin.getLogger().info("[Migration] Using table: " + actualTable);

        if (hasJsonDataColumn(conn, actualTable)) {
            plugin.getLogger().info("[Migration] Using JSON column mode (currencyData)");
            return extractBalancesFromJsonColumn(conn, actualTable);
        }

        plugin.getLogger().info("[Migration] currencyData column not found, using flat-column mode");
        return extractBalancesFromFlatColumns(conn, actualTable, knownCurrencies);
    }

    private Map<String, Map<UUID, Double>> extractBalancesFromJsonColumn(Connection conn, String tableName) throws SQLException {
        Map<String, Map<UUID, Double>> result = new HashMap<>();
        int totalRows = 0;
        int parsedRows = 0;
        int errorRows = 0;

        String sql = "SELECT " + UUID_COL + ", " + DATA_COL + " FROM " + tableName;
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                totalRows++;
                String uuidStr = rs.getString(UUID_COL);
                String jsonStr = rs.getString(DATA_COL);

                if (uuidStr == null || uuidStr.isEmpty() || jsonStr == null || jsonStr.isEmpty()) {
                    continue;
                }

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    errorRows++;
                    continue;
                }

                try {
                    parseBalancesFromJson(jsonStr, uuid, result, null);
                    parsedRows++;
                } catch (Exception e) {
                    errorRows++;
                    if (errorRows <= 5) {
                        plugin.getLogger().warning("[Migration] JSON parse error for UUID " + uuidStr + ": " + e.getMessage());
                        plugin.getLogger().warning("[Migration] Raw JSON sample: " + (jsonStr.length() > 100 ? jsonStr.substring(0, 100) + "..." : jsonStr));
                    }
                }
            }
        }

        plugin.getLogger().info("[Migration] Total rows: " + totalRows + " | Parsed: " + parsedRows + " | Errors: " + errorRows);
        return result;
    }

    private Map<String, Map<UUID, Double>> extractBalancesFromFlatColumns(Connection conn, String tableName, Set<String> knownCurrencies) throws SQLException {
        Map<String, Map<UUID, Double>> result = new HashMap<>();

        List<String> allColumns = getTableColumns(conn, tableName);
        List<String> currencyColumns = new ArrayList<>();

        for (String col : allColumns) {
            String lower = col.toLowerCase();
            if (SYSTEM_COLUMNS.contains(lower)) continue;
            if (knownCurrencies != null && knownCurrencies.contains(lower)) {
                currencyColumns.add(col);
            } else if (knownCurrencies == null) {
                currencyColumns.add(col);
            }
        }

        if (currencyColumns.isEmpty()) {
            plugin.getLogger().warning("[Migration] No currency columns detected in flat-column mode");
            return result;
        }

        plugin.getLogger().info("[Migration] Flat-column mode: detected " + currencyColumns.size() + " currency columns: " + currencyColumns);

        StringBuilder sb = new StringBuilder("SELECT ").append(UUID_COL);
        for (String col : currencyColumns) {
            sb.append(", ").append(col);
        }
        sb.append(" FROM ").append(tableName);

        int totalRows = 0;
        int parsedRows = 0;

        try (PreparedStatement ps = conn.prepareStatement(sb.toString()); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                totalRows++;
                String uuidStr = rs.getString(UUID_COL);
                if (uuidStr == null || uuidStr.isEmpty()) continue;

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                for (String col : currencyColumns) {
                    try {
                        double balance = rs.getDouble(col);
                        if (balance >= 0) {
                            result.computeIfAbsent(col.toLowerCase(), k -> new HashMap<>()).put(uuid, balance);
                        }
                    } catch (SQLException ignored) {}
                }
                parsedRows++;
            }
        }

        plugin.getLogger().info("[Migration] Total rows: " + totalRows + " | Parsed: " + parsedRows);
        return result;
    }

    private String resolveTable(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (name.equalsIgnoreCase(USER_TABLE)) return name;
            }
        }
        try (ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                String lower = name.toLowerCase();
                if (lower.contains("user") || lower.contains("player") || lower.contains("coin")) {
                    plugin.getLogger().warning("[Migration] Exact table '" + USER_TABLE + "' not found, using: " + name);
                    return name;
                }
            }
        }
        plugin.getLogger().warning("[Migration] Could not find a user table, falling back to: " + USER_TABLE);
        return USER_TABLE;
    }

    private void parseBalancesFromJson(String json, UUID uuid, Map<String, Map<UUID, Double>> result, Set<String> known) {
        json = json.trim();
        if (json.isEmpty() || json.equals("{}") || json.equals("[]") || json.equals("null")) return;

        JsonElement root = JsonParser.parseString(json);

        if (root.isJsonArray()) {
            JsonArray arr = root.getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                String cid = null;
                double balance = 0.0;

                if (obj.has("currencyId")) cid = obj.get("currencyId").getAsString();
                else if (obj.has("id")) cid = obj.get("id").getAsString();
                else if (obj.has("currency")) cid = obj.get("currency").getAsString();

                if (obj.has("value")) balance = obj.get("value").getAsDouble();
                else if (obj.has("balance")) balance = obj.get("balance").getAsDouble();
                else if (obj.has("amount")) balance = obj.get("amount").getAsDouble();

                if (cid != null && balance >= 0) {
                    result.computeIfAbsent(cid, k -> new HashMap<>()).put(uuid, balance);
                }
            }
            return;
        }

        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
                JsonElement val = entry.getValue();

                if (val.isJsonPrimitive()) {
                    try {
                        double balance = val.getAsDouble();
                        if (balance >= 0) {
                            result.computeIfAbsent(key, k -> new HashMap<>()).put(uuid, balance);
                        }
                    } catch (Exception ignored) {}
                } else if (val.isJsonObject()) {
                    JsonObject nested = val.getAsJsonObject();
                    double balance = 0.0;
                    if (nested.has("value")) balance = nested.get("value").getAsDouble();
                    else if (nested.has("balance")) balance = nested.get("balance").getAsDouble();
                    else if (nested.has("amount")) balance = nested.get("amount").getAsDouble();
                    if (balance >= 0) {
                        result.computeIfAbsent(key, k -> new HashMap<>()).put(uuid, balance);
                    }
                }
            }
        }
    }

    public void migrate(MigrationCallback callback) {
        if (!isCoinsEngineAvailable()) {
            callback.onComplete(false, 0, 0, "CoinsEngine folder not found at: " +
                new File(plugin.getDataFolder().getParentFile(), "CoinsEngine").getAbsolutePath());
            return;
        }

        plugin.getLogger().info("[Migration] === CoinsEngine Migration Starting ===");

        CompletableFuture.runAsync(() -> {
            Connection conn = null;
            try {
                CEConfig cfg = readConfig();
                plugin.getLogger().info("[Migration] DB type: " + cfg.type);

                conn = openConnection(cfg);
                plugin.getLogger().info("[Migration] Connected to CoinsEngine database");

                Map<String, CECurrencyMeta> currencyMeta = loadCurrencyMeta();
                Set<String> knownIds = currencyMeta.isEmpty() ? null : currencyMeta.keySet();

                Map<String, Map<UUID, Double>> allBalances = extractBalances(conn, knownIds);

                if (allBalances.isEmpty()) {
                    plugin.getLogger().warning("[Migration] No balance data found. Check that the database has player rows.");
                    Bukkit.getScheduler().runTask(plugin, () ->
                        callback.onComplete(false, 0, 0,
                            "No balance data found. Verify the database is correct and the table has rows."));
                    return;
                }

                plugin.getLogger().info("[Migration] Found " + allBalances.size() + " currencies with balance data");

                int totalPlayers = 0;
                int totalCurrencies = 0;
                StringBuilder details = new StringBuilder();

                for (Map.Entry<String, Map<UUID, Double>> entry : allBalances.entrySet()) {
                    String cid = entry.getKey();
                    Map<UUID, Double> balances = entry.getValue();

                    CECurrencyMeta meta = currencyMeta.getOrDefault(cid,
                        new CECurrencyMeta(cid, cid, "", 0.0, true));

                    plugin.getLogger().info("[Migration] Processing: " + cid + " (" + balances.size() + " players)");

                    final String finalCid = cid;
                    final CECurrencyMeta finalMeta = meta;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (plugin.getCurrencyManager().getCurrency(finalCid) == null) {
                            Currency newCurrency = new Currency(
                                finalCid,
                                finalMeta.name,
                                finalMeta.symbol,
                                "%amount%%symbol%",
                                "GOLD_INGOT",
                                finalMeta.startValue,
                                finalMeta.decimals,
                                true,
                                true
                            );
                            plugin.getCurrencyManager().addCurrency(newCurrency);
                            plugin.getDatabaseManager().createCurrencyTable(finalCid);
                            plugin.getLogger().info("[Migration] Created new currency: " + finalCid);
                        } else {
                            plugin.getDatabaseManager().createCurrencyTable(finalCid);
                        }
                    });

                    Thread.sleep(150);

                    plugin.getDatabaseManager().batchSetBalances(cid, balances);
                    totalPlayers = Math.max(totalPlayers, balances.size());
                    totalCurrencies++;
                    details.append("\n  ").append(cid).append(": ").append(balances.size()).append(" players");
                }

                plugin.getLogger().info("[Migration] === Migration Complete ===");
                plugin.getLogger().info("[Migration] Currencies: " + totalCurrencies + " | Max players: " + totalPlayers);

                final int finalPlayers = totalPlayers;
                final int finalCurrencies = totalCurrencies;
                final String finalDetails = details.toString();
                Bukkit.getScheduler().runTask(plugin, () ->
                    callback.onComplete(true, finalPlayers, finalCurrencies, finalDetails));

            } catch (Exception e) {
                plugin.getLogger().severe("[Migration] Failed: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () ->
                    callback.onComplete(false, 0, 0, e.getMessage()));
            } finally {
                if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
            }
        });
    }

    public CompletableFuture<List<CurrencyScanResult>> scanCurrenciesDetailed() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isCoinsEngineAvailable()) return Collections.emptyList();

            Connection conn = null;
            try {
                CEConfig cfg = readConfig();
                conn = openConnection(cfg);

                Map<String, CECurrencyMeta> meta = loadCurrencyMeta();
                Map<String, Map<UUID, Double>> allBalances = extractBalances(conn, meta.isEmpty() ? null : meta.keySet());

                Set<String> allIds = new LinkedHashSet<>();
                allIds.addAll(meta.keySet());
                allIds.addAll(allBalances.keySet());

                List<CurrencyScanResult> results = new ArrayList<>();
                for (String id : allIds) {
                    CECurrencyMeta m = meta.getOrDefault(id, new CECurrencyMeta(id, id, "", 0.0, true));
                    boolean exists = plugin.getCurrencyManager().getCurrency(id) != null;
                    int count = allBalances.containsKey(id) ? allBalances.get(id).size() : 0;
                    results.add(new CurrencyScanResult(id, m.name, m.symbol, m.startValue, m.decimals, exists, count));
                }
                return results;

            } catch (Exception e) {
                plugin.getLogger().severe("[Migration] Scan failed: " + e.getMessage());
                e.printStackTrace();
                return Collections.emptyList();
            } finally {
                if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
            }
        });
    }

    private static class CEConfig {
        final String type;
        final String host;
        final int port;
        final String database;
        final String user;
        final String pass;
        final File dbFile;

        CEConfig(String type, String host, int port, String database, String user, String pass, File dbFile) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.database = database;
            this.user = user;
            this.pass = pass;
            this.dbFile = dbFile;
        }
    }

    private static class CECurrencyMeta {
        final String id;
        final String name;
        final String symbol;
        final double startValue;
        final boolean decimals;

        CECurrencyMeta(String id, String name, String symbol, double startValue, boolean decimals) {
            this.id = id;
            this.name = name;
            this.symbol = symbol;
            this.startValue = startValue;
            this.decimals = decimals;
        }
    }
}