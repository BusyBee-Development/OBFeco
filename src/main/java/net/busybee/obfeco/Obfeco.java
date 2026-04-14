package net.busybee.obfeco;

import net.busybee.obfeco.api.ObfecoAPI;
import net.busybee.obfeco.commands.ObfecoCommand;
import net.busybee.obfeco.core.CurrencyManager;
import net.busybee.obfeco.database.DatabaseManager;
import net.busybee.obfeco.hooks.PlaceholderHook;
import net.busybee.obfeco.hooks.VaultHook;
import net.busybee.obfeco.listeners.PlayerListener;
import net.busybee.obfeco.storage.ConfigManager;
import net.busybee.obfeco.storage.MessageManager;
import net.busybee.obfeco.ui.GUIListener;
import net.busybee.obfeco.ui.GUIManager;
import net.busybee.obfeco.util.LogManager;
import net.busybee.obfeco.util.SignInput;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class Obfeco extends JavaPlugin {
    @Getter
    private static Obfeco instance;
    
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private CurrencyManager currencyManager;
    private GUIManager guiManager;
    private LogManager logManager;
    private ObfecoAPI api;
    private SignInput signInput;
    
    private VaultHook vaultHook;
    private PlaceholderHook placeholderHook;
    
    @Override
    public void onEnable() {
        instance = this;
        
        long startTime = System.currentTimeMillis();
        
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.logManager = new LogManager(this);

        if (!configManager.isConsoleEnabled()) {
            getLogger().setLevel(java.util.logging.Level.OFF);
            silenceLibraries();
        } else {
            getLogger().setLevel(java.util.logging.Level.INFO);
        }
        
        this.databaseManager = new DatabaseManager(this);
        if (!this.databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        this.currencyManager = new CurrencyManager(this);
        this.currencyManager.initialize();
        
        this.guiManager = new GUIManager();
        this.signInput = new SignInput(this);
        
        this.api = new ObfecoAPI(this);
        
        registerCommands();
        registerListeners();
        registerHooks();
        
        new Metrics(this, 29485);

        long loadTime = System.currentTimeMillis() - startTime;
        info("Obfeco v" + getDescription().getVersion() + " enabled in " + loadTime + "ms");
        info("Loaded " + currencyManager.getCurrencies().size() + " currencies");
    }
    
    @Override
    public void onDisable() {
        if (this.currencyManager != null) {
            this.currencyManager.shutdown();
        }
        
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        
        if (this.vaultHook != null) {
            this.vaultHook.unhook();
        }
        
        info("Obfeco disabled");
    }
    
    private void registerCommands() {
        ObfecoCommand obfecoCommand = new ObfecoCommand(this);
        getCommand("obfeco").setExecutor(obfecoCommand);
        getCommand("obfeco").setTabCompleter(obfecoCommand);
    }
    
    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(guiManager), this);
    }
    
    private void registerHooks() {
        if (Bukkit.getPluginManager().getPlugin("VaultUnlocked") != null && configManager.isVaultEnabled()) {
            this.vaultHook = new VaultHook(this);
            this.vaultHook.hook();
            info("Hooked into VaultUnlocked");
        }
        
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && configManager.isPlaceholderEnabled()) {
            this.placeholderHook = new PlaceholderHook(this);
            this.placeholderHook.register();
            info("Hooked into PlaceholderAPI");
        }
    }
    
    public void info(String message) {
        if (configManager != null && configManager.isLoggingEnabled() && configManager.isConsoleEnabled()) {
            getLogger().info(message);
        }
    }

    private void silenceLibraries() {
        try {
            // Loggers to silence at WARNING level
            String[] warningLoggers = {
                "com.zaxxer.hikari",
                "org.bstats",
                "net.busybee.obfeco.libs",
                "org.sqlite",
                "com.mysql",
                "net.busybee.obfeco.libs.hikari",
                "net.busybee.obfeco.libs.bstats",
                "net.busybee.obfeco.libs.slf4j",
                "net.busybee.obfeco.libs.caffeine"
            };

            // 1. JUL Silencing
            for (String name : warningLoggers) {
                java.util.logging.Logger.getLogger(name).setLevel(java.util.logging.Level.WARNING);
            }
            // Plugin logger itself
            getLogger().setLevel(java.util.logging.Level.OFF);

            // 2. Log4j2 Silencing (for Paper/Velocity)
            try {
                Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
                Object warnLevel = levelClass.getField("WARN").get(null);
                Object offLevel = levelClass.getField("OFF").get(null);
                Class<?> configuratorClass = Class.forName("org.apache.logging.log4j.core.config.Configurator");
                java.lang.reflect.Method setLevelMethod = configuratorClass.getMethod("setLevel", String.class, levelClass);

                // Silence libraries at WARN
                for (String name : warningLoggers) {
                    setLevelMethod.invoke(null, name, warnLevel);
                }
                // Silence plugin itself at OFF
                setLevelMethod.invoke(null, getName(), offLevel);
                setLevelMethod.invoke(null, "Obfeco", offLevel);
            } catch (Throwable ignored) {}
        } catch (Exception ignored) {}
    }
    
    public void reload() {
        this.configManager.reload();
        if (!configManager.isConsoleEnabled()) {
            getLogger().setLevel(java.util.logging.Level.OFF);
            silenceLibraries();
        } else {
            getLogger().setLevel(java.util.logging.Level.INFO);
        }
        this.messageManager.reload();
        this.currencyManager.loadCurrencies();
    }
}
