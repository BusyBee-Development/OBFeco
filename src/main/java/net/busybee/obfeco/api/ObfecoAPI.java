package net.busybee.obfeco.api;

import net.busybee.obfeco.Obfeco;
import net.busybee.obfeco.core.Currency;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class ObfecoAPI {
    private final Obfeco plugin;
    
    public CompletableFuture<Double> getBalance(UUID playerId, String currencyId) {
        return plugin.getCurrencyManager().getBalance(playerId, currencyId);
    }
    
    public CompletableFuture<Boolean> setBalance(UUID playerId, String currencyId, double amount) {
        return plugin.getCurrencyManager().setBalance(playerId, currencyId, amount);
    }
    
    public CompletableFuture<Boolean> addBalance(UUID playerId, String currencyId, double amount) {
        return plugin.getCurrencyManager().addBalance(playerId, currencyId, amount, false);
    }
    
    public CompletableFuture<Boolean> removeBalance(UUID playerId, String currencyId, double amount) {
        return plugin.getCurrencyManager().removeBalance(playerId, currencyId, amount, false);
    }
    
    public CompletableFuture<Boolean> hasBalance(UUID playerId, String currencyId, double amount) {
        return getBalance(playerId, currencyId).thenApply(balance -> balance >= amount);
    }
    
    public Currency getCurrency(String currencyId) {
        return plugin.getCurrencyManager().getCurrency(currencyId);
    }
    
    public Collection<Currency> getCurrencies() {
        return plugin.getCurrencyManager().getCurrencies();
    }
    
    public boolean currencyExists(String currencyId) {
        return plugin.getCurrencyManager().getCurrency(currencyId) != null;
    }
    
    public CompletableFuture<Boolean> createCurrency(Currency currency) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.getCurrencyManager().addCurrency(currency);
            return plugin.getDatabaseManager().createCurrencyTable(currency.getId());
        });
    }
    
    public CompletableFuture<Boolean> deleteCurrency(String currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.getCurrencyManager().removeCurrency(currencyId);
            return plugin.getDatabaseManager().deleteCurrencyTable(currencyId);
        });
    }
}