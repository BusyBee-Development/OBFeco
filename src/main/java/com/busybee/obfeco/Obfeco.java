package com.busybee.obfeco;

import com.busybee.obfeco.api.ObfecoAPI;
import com.busybee.obfeco.commands.ObfecoCommand;
import com.busybee.obfeco.core.CurrencyManager;
import com.busybee.obfeco.database.DatabaseManager;
import com.busybee.obfeco.hooks.PlaceholderHook;
import com.busybee.obfeco.hooks.VaultHook;
import com.busybee.obfeco.listeners.PlayerListener;
import com.busybee.obfeco.storage.ConfigManager;
import com.busybee.obfeco.storage.MessageManager;
import com.busybee.obfeco.ui.GUIListener;
import com.busybee.obfeco.ui.GUIManager;
import com.busybee.obfeco.util.LogManager;
import com.busybee.obfeco.util.SignInput;
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
        getLogger().info("Obfeco v" + getDescription().getVersion() + " enabled in " + loadTime + "ms");
        getLogger().info("Loaded " + currencyManager.getCurrencies().size() + " currencies");
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
        
        getLogger().info("Obfeco disabled");
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
            getLogger().info("Hooked into VaultUnlocked");
        }
        
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && configManager.isPlaceholderEnabled()) {
            this.placeholderHook = new PlaceholderHook(this);
            this.placeholderHook.register();
            getLogger().info("Hooked into PlaceholderAPI");
        }
    }
    
    public void reload() {
        this.configManager.reload();
        this.messageManager.reload();
        this.currencyManager.loadCurrencies();
    }
}
