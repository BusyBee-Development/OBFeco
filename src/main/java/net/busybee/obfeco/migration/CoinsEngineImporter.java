package net.busybee.obfeco.migration;

import com.google.gson.*;
import com.zaxxer.hikari.HikariDataSource;
import net.busybee.obfeco.Obfeco;
import net.busybee.obfeco.core.Currency;
import net.busybee.obfeco.util.HikariDataSourceBuilder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CoinsEngineImporter {

    private final Obfeco plugin;

    public CoinsEngineImporter(Obfeco plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<ImportResult> processMigration() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HikariDataSource dataSource = this.readConfig();

                this.plugin.getLogger().info("Importing all the data from coins engine!");

                ImportResult result;
                try (Connection conn = dataSource.getConnection()) {
                    result = this.importAll(conn);
                }

                dataSource.close();
                return result;

            } catch (Exception e) {
                e.printStackTrace();
                return new ImportResult(0, 0, null);
            }
        });
    }

    public CompletableFuture<List<CurrencyScanResult>> scanCurrenciesDetailed(boolean debug) {
        return CompletableFuture.supplyAsync(() -> {
            try (HikariDataSource dataSource = readConfig();
                 Connection connection = dataSource.getConnection()) {

                Map<String, CurrencyMeta> currencyMeta = loadCurrencyMeta();
                Set<String> detectedCurrencies = new HashSet<>();

                String tableName = resolveTable(connection);

                String sql = """
                SELECT currencyData
                FROM %s
                LIMIT 100
                """.formatted(tableName);

                try (PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet resultSet = statement.executeQuery()) {

                    while (resultSet.next()) {
                        String rawCurrencyData = resultSet.getString("currencyData");
                        if (rawCurrencyData == null || rawCurrencyData.isBlank()) {
                            continue;
                        }

                        try {
                            JsonArray currencyArray = JsonParser.parseString(rawCurrencyData).getAsJsonArray();

                            for (JsonElement element : currencyArray) {
                                if (!element.isJsonObject()) {
                                    continue;
                                }

                                JsonObject currencyObject = element.getAsJsonObject();

                                if (!currencyObject.has("currencyId")) {
                                    continue;
                                }

                                detectedCurrencies.add(currencyObject.get("currencyId").getAsString());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (debug) {
                    plugin.info("[Scan] Detected currencies: " + detectedCurrencies);
                }

                Set<String> allCurrencies = new LinkedHashSet<>();
                allCurrencies.addAll(currencyMeta.keySet());
                allCurrencies.addAll(detectedCurrencies);

                List<CurrencyScanResult> results = new ArrayList<>();

                for (String currencyId : allCurrencies) {
                    CurrencyMeta meta = currencyMeta.getOrDefault(
                            currencyId,
                            new CurrencyMeta(currencyId, currencyId, "", 0.0D, true)
                    );

                    boolean alreadyExists = plugin.getCurrencyManager().getCurrency(currencyId) != null;

                    results.add(new CurrencyScanResult(
                            currencyId,
                            meta.name,
                            meta.symbol,
                            meta.start,
                            meta.decimals,
                            alreadyExists,
                            0
                    ));
                }

                return results;
            } catch (Exception exception) {
                exception.printStackTrace();
                return Collections.emptyList();
            }
        });
    }

    public ImportResult importAll(Connection conn) throws Exception {

        Map<String, CurrencyMeta> meta = loadCurrencyMeta();
        Map<String, Map<UUID, Double>> allBalances = extractBalances(conn);

        int totalCurrencies = 0;
        int totalPlayers = 0;

        StringBuilder details = new StringBuilder();


        for (Map.Entry<String, Map<UUID, Double>> entry : allBalances.entrySet()) {

            String cid = entry.getKey();
            Map<UUID, Double> balances = entry.getValue();

            CurrencyMeta m = meta.getOrDefault(cid,
                    new CurrencyMeta(cid, cid, "", 0.0, true));

            plugin.info("[Migration] Processing: " + cid + " (" + balances.size() + " players)");

            if (plugin.getCurrencyManager().getCurrency(cid) == null) {
                Currency currency = new Currency(
                        cid,
                        m.name,
                        m.symbol,
                        "%amount%%symbol%",
                        "GOLD_INGOT",
                        m.start,
                        m.decimals,
                        true,
                        true
                );

                plugin.getCurrencyManager().addCurrency(currency);
                plugin.info("[Migration] Registered currency: " + cid);
            }

            if (!plugin.getDatabaseManager().createCurrencyTable(cid)) {
                plugin.getLogger().severe("[Migration] Failed to create table for: " + cid);
                continue;
            }

            plugin.getDatabaseManager().batchSetBalances(cid, balances);

            totalCurrencies++;
            totalPlayers = Math.max(totalPlayers, balances.size());
            details.append("\n  ").append(cid).append(": ").append(balances.size()).append(" players");
        }

        plugin.info("[Migration] Done. Currencies=" + totalCurrencies + ", Players=" + totalPlayers);

        return new ImportResult(totalCurrencies, totalPlayers, details.toString());
    }

    public boolean isCoinsEngineAvailable() {
        return new File(plugin.getDataFolder().getParentFile(), "CoinsEngine").exists();
    }

    private HikariDataSource readConfig() throws Exception {
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
            return HikariDataSourceBuilder.createMySql(host, port, database, user, pass);
        }

        File db = new File(folder, "data.db");
        if (!db.exists()) db = new File(folder, "coinsengine.db");
        if (!db.exists()) {
            File[] dbFiles = folder.listFiles((d, n) -> n.endsWith(".db"));
            if (dbFiles != null && dbFiles.length > 0) db = dbFiles[0];
        }
        if (!db.exists()) throw new Exception("CoinsEngine SQLite database not found in: " + folder.getAbsolutePath());
        return HikariDataSourceBuilder.createSqlite(db);
    }

    private Map<String, Map<UUID, Double>> extractBalances(Connection connection) throws SQLException {
        Map<String, Map<UUID, Double>> balancesByCurrency = new HashMap<>();

        String tableName = resolveTable(connection);
        String uuidColumn = resolveUuidColumn(connection, tableName);

        String sql = """
        SELECT %s, currencyData
        FROM %s
        """.formatted(uuidColumn, tableName);

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                UUID uuid = parseUuid(resultSet.getString(uuidColumn));
                if (uuid == null) {
                    continue;
                }

                String rawCurrencyData = resultSet.getString("currencyData");
                if (rawCurrencyData == null || rawCurrencyData.isBlank()) {
                    continue;
                }

                parseCurrencyData(rawCurrencyData, uuid, balancesByCurrency);
            }
        }

        return balancesByCurrency;
    }

    private void parseCurrencyData(
            String rawCurrencyData,
            UUID uuid,
            Map<String, Map<UUID, Double>> balancesByCurrency
    ) {
        try {
            JsonArray currencyArray = JsonParser.parseString(rawCurrencyData).getAsJsonArray();

            for (JsonElement element : currencyArray) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject currencyObject = element.getAsJsonObject();

                if (!currencyObject.has("currencyId") || !currencyObject.has("balance")) {
                    continue;
                }

                String currencyId = currencyObject.get("currencyId").getAsString();
                double balance = currencyObject.get("balance").getAsDouble();

                if (currencyId == null || currencyId.isBlank()) {
                    continue;
                }

                if (balance <= 0.0D) {
                    continue;
                }

                balancesByCurrency
                        .computeIfAbsent(currencyId, ignored -> new HashMap<>())
                        .put(uuid, balance);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("[Migration] Failed to parse currencyData JSON");
            plugin.getLogger().warning("[Migration] JSON: " + rawCurrencyData);
        }
    }


    private Map<String, CurrencyMeta> loadCurrencyMeta() {

        Map<String, CurrencyMeta> map = new HashMap<>();

        File dir = new File(plugin.getDataFolder().getParentFile(), "CoinsEngine/currencies");

        if (!dir.exists()) return map;

        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return map;

        for (File f : files) {
            var cfg = YamlConfiguration.loadConfiguration(f);

            String id = f.getName().replace(".yml", "");

            map.put(id, new CurrencyMeta(
                    id,
                    cfg.getString("Name", id),
                    cfg.getString("Symbol", ""),
                    cfg.getDouble("Start_Value", 0),
                    cfg.getBoolean("Decimals_Allowed", true)
            ));
        }

        return map;
    }

    private UUID parseUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private String resolveTable(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData()
                .getTables(null, null, "%", new String[]{"TABLE"})) {

            while (rs.next()) {
                String name = rs.getString("TABLE_NAME").toLowerCase();
                if (name.contains("user")) {
                    return rs.getString("TABLE_NAME");
                }
            }
        }
        return "coinsengine_users";
    }

    private String resolveUuidColumn(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData()
                .getColumns(null, null, table, null)) {

            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col.toLowerCase().contains("uuid")) return col;
            }
        }
        return "uuid";
    }


    private record CurrencyMeta(
            String id,
            String name,
            String symbol,
            double start,
            boolean decimals
    ) {}

    public record ImportResult(
            int currencies,
            int players,
            String details
    ) {}
}
