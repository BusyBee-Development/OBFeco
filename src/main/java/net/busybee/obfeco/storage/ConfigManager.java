package net.busybee.obfeco.storage;

import net.busybee.obfeco.Obfeco;
import net.busybee.obfeco.core.Currency;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
public class ConfigManager {
    private final Obfeco plugin;
    private FileConfiguration config;

    private String storageType;
    private String databaseHost;
    private int databasePort;
    private String databaseName;
    private String databaseUsername;
    private String databasePassword;
    private int databasePoolSize;
    private int databaseConnectionTimeout;
    private int databaseMaxLifetime;

    private int batchSaveInterval;
    private boolean autoSaveOnQuit;
    private boolean useCaffeine;

    private String thousandsSuffix;
    private String millionsSuffix;
    private String billionsSuffix;
    private String trillionsSuffix;
    private String quadrillionsSuffix;
    private int decimalPlaces;

    private boolean vaultEnabled;
    private String primaryCurrency;

    private boolean placeholderEnabled;
    private int topCacheMinutes;

    private boolean loggingEnabled;
    private boolean consoleEnabled;
    private boolean fileEnabled;
    private boolean adminTransactions;
    private boolean userTransactions;

    private String migrationType;
    private String migrationTable;
    private String migrationFile;
    private java.util.Map<String, String> migrationMappings;

    public ConfigManager(Obfeco plugin) {
        this.plugin = plugin;
        this.plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        load();
    }

    public void load() {
        this.storageType = config.getString("storage-type", "YAML").toUpperCase();

        this.databaseHost = config.getString("database.host");
        this.databasePort = config.getInt("database.port");
        this.databaseName = config.getString("database.database");
        this.databaseUsername = config.getString("database.username");
        this.databasePassword = config.getString("database.password");
        this.databasePoolSize = config.getInt("database.pool-size", 10);
        this.databaseConnectionTimeout = config.getInt("database.connection-timeout", 30000);
        this.databaseMaxLifetime = config.getInt("database.max-lifetime", 1800000);

        this.batchSaveInterval = config.getInt("cache.batch-save-interval", 30);
        this.autoSaveOnQuit = config.getBoolean("cache.auto-save-on-quit", true);
        this.useCaffeine = config.getBoolean("cache.use-caffeine", true);

        this.thousandsSuffix = config.getString("formatting.thousands", "k");
        this.millionsSuffix = config.getString("formatting.millions", "M");
        this.billionsSuffix = config.getString("formatting.billions", "B");
        this.trillionsSuffix = config.getString("formatting.trillions", "T");
        this.quadrillionsSuffix = config.getString("formatting.quadrillions", "Q");
        this.decimalPlaces = config.getInt("formatting.decimal-places", 2);

        this.vaultEnabled = config.getBoolean("vault.enabled", true);
        this.primaryCurrency = config.getString("vault.primary-currency", "dollars");

        this.placeholderEnabled = config.getBoolean("placeholder.enabled", true);
        this.topCacheMinutes = config.getInt("placeholder.top-cache-minutes", 5);

        this.consoleEnabled = config.getBoolean("logging.console", config.getBoolean("logging.console-enabled", config.getBoolean("logging.console-notifications", config.getBoolean("logging.enabled", true))));
        this.fileEnabled = config.getBoolean("logging.file", config.getBoolean("logging.file-enabled", true));
        this.loggingEnabled = this.consoleEnabled || this.fileEnabled;
        this.adminTransactions = config.getBoolean("logging.admin-transactions", true);
        this.userTransactions = config.getBoolean("logging.user-transactions", true);

        this.migrationType = config.getString("migration.coinsengine.type", "sqlite");
        this.migrationTable = config.getString("migration.coinsengine.table", "coinsengine_users");
        this.migrationFile = config.getString("migration.coinsengine.file", "plugins/CoinsEngine/coinsengine.db");
        
        this.migrationMappings = new java.util.HashMap<>();
        if (config.contains("migration.coinsengine.mappings")) {
            org.bukkit.configuration.ConfigurationSection mappingsSection = config.getConfigurationSection("migration.coinsengine.mappings");
            if (mappingsSection != null) {
                for (String key : mappingsSection.getKeys(false)) {
                    this.migrationMappings.put(key, mappingsSection.getString(key));
                }
            }
        }
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        load();
    }

    public boolean isConsoleEnabled() {
        return consoleEnabled;
    }

    public boolean isFileEnabled() {
        return fileEnabled;
    }

    public String formatAmount(double amount, Currency currency) {
        if (currency != null && !currency.isUseDecimals()) {
            long whole = (long) Math.floor(amount);
            if (whole < 1000) {
                return applyFormat(String.valueOf(whole), currency);
            } else if (whole < 1000000) {
                return applyFormat(String.format("%d%s", whole / 1000, thousandsSuffix), currency);
            } else if (whole < 1000000000L) {
                return applyFormat(String.format("%d%s", whole / 1000000, millionsSuffix), currency);
            } else if (whole < 1000000000000L) {
                return applyFormat(String.format("%d%s", whole / 1000000000L, billionsSuffix), currency);
            } else if (whole < 1000000000000000L) {
                return applyFormat(String.format("%d%s", whole / 1000000000000L, trillionsSuffix), currency);
            } else {
                return applyFormat(String.format("%d%s", whole / 1000000000000000L, quadrillionsSuffix), currency);
            }
        }

        String formattedAmount;
        if (amount < 1000) {
            formattedAmount = String.format("%." + decimalPlaces + "f", amount);
        } else if (amount < 1000000) {
            formattedAmount = String.format("%." + decimalPlaces + "f%s", amount / 1000.0, thousandsSuffix);
        } else if (amount < 1000000000) {
            formattedAmount = String.format("%." + decimalPlaces + "f%s", amount / 1000000.0, millionsSuffix);
        } else if (amount < 1000000000000L) {
            formattedAmount = String.format("%." + decimalPlaces + "f%s", amount / 1000000000.0, billionsSuffix);
        } else if (amount < 1000000000000000L) {
            formattedAmount = String.format("%." + decimalPlaces + "f%s", amount / 1000000000000.0, trillionsSuffix);
        } else {
            formattedAmount = String.format("%." + decimalPlaces + "f%s", amount / 1000000000000000.0, quadrillionsSuffix);
        }

        return applyFormat(formattedAmount, currency);
    }

    private String applyFormat(String formattedAmount, Currency currency) {
        if (currency != null && currency.getFormat() != null) {
            return currency.getFormat()
                    .replace("%amount%", formattedAmount)
                    .replace("%symbol%", currency.getSymbol() != null ? currency.getSymbol() : "");
        }
        return formattedAmount;
    }

    public String formatAmount(double amount) {
        return formatAmount(amount, null);
    }
}
