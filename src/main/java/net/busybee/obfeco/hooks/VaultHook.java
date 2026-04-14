package net.busybee.obfeco.hooks;

import net.busybee.obfeco.Obfeco;
import lombok.RequiredArgsConstructor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class VaultHook implements Economy {
    private final Obfeco plugin;
    
    public void hook() {
        Bukkit.getServicesManager().register(Economy.class, this, plugin, ServicePriority.Highest);
    }
    
    public void unhook() {
        Bukkit.getServicesManager().unregister(Economy.class, this);
    }
    
    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }
    
    @Override
    public String getName() {
        return "Obfeco";
    }
    
    @Override
    public boolean hasBankSupport() {
        return false;
    }
    
    @Override
    public int fractionalDigits() {
        return plugin.getConfigManager().getDecimalPlaces();
    }
    
    @Override
    public String format(double amount) {
        return plugin.getConfigManager().formatAmount(amount);
    }
    
    @Override
    public String currencyNamePlural() {
        return plugin.getCurrencyManager().getCurrency(plugin.getConfigManager().getPrimaryCurrency()).getDisplayName();
    }
    
    @Override
    public String currencyNameSingular() {
        return plugin.getCurrencyManager().getCurrency(plugin.getConfigManager().getPrimaryCurrency()).getDisplayName();
    }
    
    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }
    
    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }
    
    @Override
    public boolean hasAccount(String playerName, String world) {
        return true;
    }
    
    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return true;
    }
    
    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return getBalance(player);
    }
    
    @Override
    public double getBalance(OfflinePlayer player) {
        try {
            return plugin.getCurrencyManager().getBalance(player.getUniqueId(), plugin.getConfigManager().getPrimaryCurrency()).get();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }
    
    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }
    
    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }
    
    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }
    
    @Override
    public boolean has(String playerName, String world, double amount) {
        return has(playerName, amount);
    }
    
    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }
    
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        try {
            boolean success = plugin.getCurrencyManager().removeBalance(player.getUniqueId(), plugin.getConfigManager().getPrimaryCurrency(), amount, true).get();
            if (success) {
                double newBalance = getBalance(player);
                return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
            } else {
                return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
            }
        } catch (Exception e) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, e.getMessage());
        }
    }
    
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String world, double amount) {
        return withdrawPlayer(playerName, amount);
    }
    
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        try {
            boolean success = plugin.getCurrencyManager().addBalance(player.getUniqueId(), plugin.getConfigManager().getPrimaryCurrency(), amount, true).get();
            if (success) {
                double newBalance = getBalance(player);
                return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
            } else {
                return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Failed to add balance");
            }
        } catch (Exception e) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, e.getMessage());
        }
    }
    
    @Override
    public EconomyResponse depositPlayer(String playerName, String world, double amount) {
        return depositPlayer(playerName, amount);
    }
    
    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    
    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }
    
    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }
    
    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }
    
    @Override
    public boolean createPlayerAccount(String playerName, String world) {
        return true;
    }
    
    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return true;
    }
}
