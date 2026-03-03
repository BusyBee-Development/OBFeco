package com.busybee.obfeco.core;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.api.events.CurrencyChangeEvent;
import com.busybee.obfeco.util.ColorUtil;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class CurrencyManager {
    private final Obfeco plugin;
    
    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> balanceCache = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> dirtyPlayersByCurrency = new ConcurrentHashMap<>();
    
    private BukkitTask batchSaveTask;
    
    public void initialize() {
        loadCurrencies();
        startBatchSaveTask();
    }
    
    public void loadCurrencies() {
        currencies.clear();
        File folder = new File(plugin.getDataFolder(), "currencies");
        if (!folder.exists()) {
            folder.mkdirs();
            createDefaultCurrencyFile();
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String id = file.getName().replace(".yml", "");
            String displayName = config.getString("display-name", id);
            String symbol = config.getString("symbol", "");
            String format = config.getString("format", "%amount%%symbol%");
            String material = config.getString("material", "GOLD_INGOT");
            double startingBalance = config.getDouble("starting-balance", 0.0);
            boolean useDecimals = config.getBoolean("use-decimals", true);
            boolean notifyGive = config.getBoolean("notify-give", true);
            boolean notifyTake = config.getBoolean("notify-take", true);

            boolean needsSave = false;
            if (!config.contains("notify-give")) {
                config.set("notify-give", true);
                needsSave = true;
            }
            if (!config.contains("notify-take")) {
                config.set("notify-take", true);
                needsSave = true;
            }
            if (needsSave) {
                try {
                    config.save(file);
                    plugin.getLogger().info("Updated currency file " + id + ".yml with missing notify settings");
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to update currency file " + id + ".yml: " + e.getMessage());
                }
            }

            Currency currency = new Currency(id, displayName, symbol, format, material, startingBalance, useDecimals, notifyGive, notifyTake);
            currencies.put(currency.getId(), currency);
            dirtyPlayersByCurrency.put(currency.getId(), ConcurrentHashMap.newKeySet());
            plugin.getDatabaseManager().createCurrencyTable(id);
        }
    }

    private void createDefaultCurrencyFile() {
        File file = new File(plugin.getDataFolder(), "currencies/dollars.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("display-name", "<gold>Dollars");
        config.set("symbol", "$");
        config.set("format", "%symbol%%amount%");
        config.set("material", "GOLD_INGOT");
        config.set("starting-balance", 0.0);
        config.set("use-decimals", true);
        config.set("notify-give", true);
        config.set("notify-take", true);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveCurrency(Currency currency) {
        File folder = new File(plugin.getDataFolder(), "currencies");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File file = new File(folder, currency.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("display-name", currency.getDisplayName());
        config.set("symbol", currency.getSymbol());
        config.set("format", currency.getFormat());
        config.set("material", currency.getMaterial());
        config.set("starting-balance", currency.getStartingBalance());
        config.set("use-decimals", currency.isUseDecimals());
        config.set("notify-give", currency.isNotifyGive());
        config.set("notify-take", currency.isNotifyTake());
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void addCurrency(Currency currency) {
        currencies.put(currency.getId(), currency);
        dirtyPlayersByCurrency.put(currency.getId(), ConcurrentHashMap.newKeySet());
        saveCurrency(currency);
    }
    
    public void removeCurrency(String currencyId) {
        currencies.remove(currencyId);
        balanceCache.values().forEach(map -> map.remove(currencyId));
        dirtyPlayersByCurrency.remove(currencyId);
        File file = new File(plugin.getDataFolder(), "currencies/" + currencyId + ".yml");
        if (file.exists()) file.delete();
    }
    
    public Currency getCurrency(String currencyId) {
        if (currencyId == null) return null;
        Currency c = currencies.get(currencyId);
        if (c != null) return c;
        for (Currency currency : currencies.values()) {
            if (currency.getId().equalsIgnoreCase(currencyId)) {
                return currency;
            }
        }
        return null;
    }
    
    public Collection<Currency> getCurrencies() {
        return currencies.values();
    }
    
    public CompletableFuture<Double> getBalance(UUID playerId, String currencyId) {
        Currency currency = getCurrency(currencyId);
        String canonicalId = currency != null ? currency.getId() : currencyId;
        
        Map<String, Double> playerBalances = balanceCache.get(playerId);
        
        if (playerBalances != null && playerBalances.containsKey(canonicalId)) {
            return CompletableFuture.completedFuture(playerBalances.get(canonicalId));
        }

        if (Bukkit.isPrimaryThread()) {
            double balance = plugin.getDatabaseManager().getBalance(playerId, canonicalId);
            balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, balance);
            return CompletableFuture.completedFuture(balance);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            double balance = plugin.getDatabaseManager().getBalance(playerId, canonicalId);
            balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, balance);
            return balance;
        });
    }

    public double getBalanceSync(UUID playerId, String currencyId) {
        Currency currency = getCurrency(currencyId);
        String canonicalId = currency != null ? currency.getId() : currencyId;
        
        Map<String, Double> playerBalances = balanceCache.get(playerId);
        
        if (playerBalances != null && playerBalances.containsKey(canonicalId)) {
            return playerBalances.get(canonicalId);
        }
        
        double balance = plugin.getDatabaseManager().getBalance(playerId, canonicalId);
        balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, balance);
        return balance;
    }
    
    public CompletableFuture<Boolean> setBalance(UUID playerId, String currencyId, double amount) {
        if (amount < 0) return CompletableFuture.completedFuture(false);
        
        Currency currency = getCurrency(currencyId);
        String canonicalId = currency != null ? currency.getId() : currencyId;
        
        return getBalance(playerId, canonicalId).thenCompose(oldBalance -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || Bukkit.isPrimaryThread()) {
                balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, amount);
                markDirty(playerId, canonicalId);
                
                if (player != null) {
                    CurrencyChangeEvent event = new CurrencyChangeEvent(player, canonicalId, oldBalance, amount, CurrencyChangeEvent.ChangeType.SET);
                    Bukkit.getPluginManager().callEvent(event);
                    
                    if (event.isCancelled()) {
                        balanceCache.get(playerId).put(canonicalId, oldBalance);
                        return CompletableFuture.completedFuture(false);
                    }
                }
                return CompletableFuture.completedFuture(true);
            } else {
                CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, amount);
                    markDirty(playerId, canonicalId);
                    
                    CurrencyChangeEvent event = new CurrencyChangeEvent(player, canonicalId, oldBalance, amount, CurrencyChangeEvent.ChangeType.SET);
                    Bukkit.getPluginManager().callEvent(event);
                    
                    if (event.isCancelled()) {
                        balanceCache.get(playerId).put(canonicalId, oldBalance);
                        resultFuture.complete(false);
                    } else {
                        resultFuture.complete(true);
                    }
                });
                return resultFuture;
            }
        });
    }
    
    public CompletableFuture<Boolean> addBalance(UUID playerId, String currencyId, double amount, boolean silent) {
        if (amount <= 0) return CompletableFuture.completedFuture(false);
        
        Currency currency = getCurrency(currencyId);
        String canonicalId = currency != null ? currency.getId() : currencyId;
        
        return getBalance(playerId, canonicalId).thenCompose(currentBalance -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || Bukkit.isPrimaryThread()) {
                double newBalance = currentBalance + amount;
                balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, newBalance);
                markDirty(playerId, canonicalId);
                
                if (player != null) {
                    CurrencyChangeEvent event = new CurrencyChangeEvent(player, canonicalId, currentBalance, newBalance, CurrencyChangeEvent.ChangeType.ADD);
                    Bukkit.getPluginManager().callEvent(event);
                    
                    if (event.isCancelled()) {
                        balanceCache.get(playerId).put(canonicalId, currentBalance);
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    if (!silent && currency != null && currency.isNotifyGive()) {
                        player.sendMessage(ColorUtil.colorize(
                            plugin.getMessageManager().getMessage("transaction.receive")
                                .replace("{amount}", plugin.getConfigManager().formatAmount(amount, currency))
                                .replace("{currency}", currency.getDisplayName())
                        ));
                    }
                }
                return CompletableFuture.completedFuture(true);
            } else {
                CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double newBalance = currentBalance + amount;
                    balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, newBalance);
                    markDirty(playerId, canonicalId);
                    
                    CurrencyChangeEvent event = new CurrencyChangeEvent(player, canonicalId, currentBalance, newBalance, CurrencyChangeEvent.ChangeType.ADD);
                    Bukkit.getPluginManager().callEvent(event);
                    
                    if (event.isCancelled()) {
                        balanceCache.get(playerId).put(canonicalId, currentBalance);
                        resultFuture.complete(false);
                    } else {
                        if (!silent && currency != null && currency.isNotifyGive()) {
                            player.sendMessage(ColorUtil.colorize(
                                plugin.getMessageManager().getMessage("transaction.receive")
                                    .replace("{amount}", plugin.getConfigManager().formatAmount(amount, currency))
                                    .replace("{currency}", currency.getDisplayName())
                            ));
                        }
                        resultFuture.complete(true);
                    }
                });
                return resultFuture;
            }
        });
    }
    
    public CompletableFuture<Boolean> removeBalance(UUID playerId, String currencyId, double amount, boolean silent) {
        if (amount <= 0) return CompletableFuture.completedFuture(false);
        
        Currency currency = getCurrency(currencyId);
        String canonicalId = currency != null ? currency.getId() : currencyId;
        
        return getBalance(playerId, canonicalId).thenCompose(currentBalance -> {
            if (currentBalance < amount) return CompletableFuture.completedFuture(false);
            
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || Bukkit.isPrimaryThread()) {
                double newBalance = currentBalance - amount;
                balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, newBalance);
                markDirty(playerId, canonicalId);
                
                if (player != null) {
                    CurrencyChangeEvent event = new CurrencyChangeEvent(player, canonicalId, currentBalance, newBalance, CurrencyChangeEvent.ChangeType.REMOVE);
                    Bukkit.getPluginManager().callEvent(event);
                    
                    if (event.isCancelled()) {
                        balanceCache.get(playerId).put(canonicalId, currentBalance);
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    if (!silent && currency != null && currency.isNotifyTake()) {
                        player.sendMessage(ColorUtil.colorize(
                            plugin.getMessageManager().getMessage("transaction.lose")
                                .replace("{amount}", plugin.getConfigManager().formatAmount(amount, currency))
                                .replace("{currency}", currency.getDisplayName())
                        ));
                    }
                }
                return CompletableFuture.completedFuture(true);
            } else {
                CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double newBalance = currentBalance - amount;
                    balanceCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(canonicalId, newBalance);
                    markDirty(playerId, canonicalId);
                    
                    CurrencyChangeEvent event = new CurrencyChangeEvent(player, canonicalId, currentBalance, newBalance, CurrencyChangeEvent.ChangeType.REMOVE);
                    Bukkit.getPluginManager().callEvent(event);
                    
                    if (event.isCancelled()) {
                        balanceCache.get(playerId).put(canonicalId, currentBalance);
                        resultFuture.complete(false);
                    } else {
                        if (!silent && currency != null && currency.isNotifyTake()) {
                            player.sendMessage(ColorUtil.colorize(
                                plugin.getMessageManager().getMessage("transaction.lose")
                                    .replace("{amount}", plugin.getConfigManager().formatAmount(amount, currency))
                                    .replace("{currency}", currency.getDisplayName())
                            ));
                        }
                        resultFuture.complete(true);
                    }
                });
                return resultFuture;
            }
        });
    }

    private void markDirty(UUID playerId, String currencyId) {
        dirtyPlayersByCurrency.computeIfAbsent(currencyId, k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }
    
    public void loadPlayerData(UUID playerId) {
        CompletableFuture.runAsync(() -> {
            Map<String, Double> balances = new ConcurrentHashMap<>();
            for (Currency currency : currencies.values()) {
                double balance = plugin.getDatabaseManager().getBalance(playerId, currency.getId());
                balances.put(currency.getId(), balance);
            }
            balanceCache.put(playerId, balances);
        });
    }
    
    public void savePlayerData(UUID playerId) {
        Map<String, Double> playerBalances = balanceCache.get(playerId);
        if (playerBalances == null) return;
        
        CompletableFuture.runAsync(() -> {
            for (String currencyId : currencies.keySet()) {
                Set<UUID> dirty = dirtyPlayersByCurrency.get(currencyId);
                if (dirty != null && dirty.contains(playerId)) {
                    Double balance = playerBalances.get(currencyId);
                    if (balance != null) {
                        plugin.getDatabaseManager().setBalance(playerId, currencyId, balance);
                        dirty.remove(playerId);
                    }
                }
            }
        });
    }
    
    public void unloadPlayerData(UUID playerId) {
        savePlayerData(playerId);
        balanceCache.remove(playerId);
    }
    
    private void startBatchSaveTask() {
        int interval = plugin.getConfigManager().getBatchSaveInterval() * 20;
        
        this.batchSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (String currencyId : currencies.keySet()) {
                Set<UUID> dirty = dirtyPlayersByCurrency.get(currencyId);
                if (dirty == null || dirty.isEmpty()) continue;

                Map<UUID, Double> toSave = new HashMap<>();
                for (UUID playerId : new HashSet<>(dirty)) {
                    Map<String, Double> balances = balanceCache.get(playerId);
                    if (balances != null && balances.containsKey(currencyId)) {
                        toSave.put(playerId, balances.get(currencyId));
                    }
                }

                if (!toSave.isEmpty()) {
                    plugin.getDatabaseManager().batchSetBalances(currencyId, toSave);
                    dirty.removeAll(toSave.keySet());
                    
                    // Clear from RAM after saving to database
                    for (UUID playerId : toSave.keySet()) {
                        if (Bukkit.getPlayer(playerId) == null) {
                            balanceCache.remove(playerId);
                        }
                    }
                }
            }
        }, interval, interval);
    }
    
    public void shutdown() {
        if (batchSaveTask != null) batchSaveTask.cancel();
        
        for (String currencyId : currencies.keySet()) {
            Set<UUID> dirty = dirtyPlayersByCurrency.get(currencyId);
            if (dirty == null || dirty.isEmpty()) continue;

            Map<UUID, Double> toSave = new HashMap<>();
            for (UUID playerId : dirty) {
                Map<String, Double> balances = balanceCache.get(playerId);
                if (balances != null && balances.containsKey(currencyId)) {
                    toSave.put(playerId, balances.get(currencyId));
                }
            }
            if (!toSave.isEmpty()) {
                plugin.getDatabaseManager().batchSetBalances(currencyId, toSave);
            }
        }
        
        balanceCache.clear();
        dirtyPlayersByCurrency.clear();
    }
}
