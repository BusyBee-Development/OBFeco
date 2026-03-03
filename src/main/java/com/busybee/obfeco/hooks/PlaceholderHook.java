package com.busybee.obfeco.hooks;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import com.busybee.obfeco.database.DatabaseManager.LeaderboardEntry;
import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PlaceholderHook extends PlaceholderExpansion {
    private final Obfeco plugin;
    private final Map<String, List<LeaderboardEntry>> topCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastCacheUpdate = new ConcurrentHashMap<>();

    @Override
    public @NotNull String getIdentifier() {
        return "obfeco";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BusyBee";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.isEmpty()) return null;

        String[] parts = params.split("_");
        if (parts.length == 0) return null;

        // 1. Try prefix-based: %obfeco_<currency>_<type>%
        for (int i = parts.length; i > 0; i--) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                if (j > 0) sb.append("_");
                sb.append(parts[j]);
            }
            String testId = sb.toString();
            Currency testCurrency = plugin.getCurrencyManager().getCurrency(testId);
            if (testCurrency != null) {
                String remaining = params.substring(testId.length());
                if (remaining.startsWith("_")) remaining = remaining.substring(1);
                
                return processPlaceholder(player, testCurrency, testCurrency.getId(), remaining);
            }
        }

        if (parts.length >= 2) {
            String type = parts[0].toLowerCase();
            // Handle balance_<currency>, formatted_<currency>, raw_<currency>
            if (type.equals("balance") || type.equals("formatted") || type.equals("raw") || type.equals("symbol") || type.equals("amount")) {
                String currencyId = params.substring(parts[0].length() + 1);
                Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
                if (currency != null) {
                    return processPlaceholder(player, currency, currency.getId(), type);
                }
            }

            if (type.equals("top") && parts.length >= 3) {
                int position = -1;
                String currencySearch = null;
                String remainingType = "value";

                for (int i = 1; i < parts.length; i++) {
                    try {
                        position = Integer.parseInt(parts[i]);
                        break;
                    } catch (NumberFormatException ignored) {}
                }

                if (position != -1) {
                    for (int i = 1; i < parts.length; i++) {
                        String part = parts[i];
                        if (part.equals(String.valueOf(position))) continue;
                        for (int j = parts.length; j > i; j--) {
                            StringBuilder sb = new StringBuilder();
                            for (int k = i; k < j; k++) {
                                if (k > i) sb.append("_");
                                sb.append(parts[k]);
                            }
                            String testId = sb.toString();
                            Currency testCurrency = plugin.getCurrencyManager().getCurrency(testId);
                            if (testCurrency != null) {
                                currencySearch = testCurrency.getId();
                                StringBuilder typeBuilder = new StringBuilder();
                                for (int k = 1; k < parts.length; k++) {
                                    if (k >= i && k < j) continue;
                                    if (k == 0) continue;
                                    if (parts[k].equals(String.valueOf(position))) continue;
                                    
                                    if (typeBuilder.length() > 0) typeBuilder.append("_");
                                    typeBuilder.append(parts[k]);
                                }
                                if (typeBuilder.length() > 0) remainingType = typeBuilder.toString();
                                
                                return processPlaceholder(player, testCurrency, currencySearch, "top_" + position + "_" + remainingType);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private String processPlaceholder(OfflinePlayer player, Currency currency, String currencyId, String remaining) {
        if (remaining.equalsIgnoreCase("formatted") || remaining.equalsIgnoreCase("balance") || remaining.equalsIgnoreCase("amount")) {
            if (player == null) return "0";
            double balance = plugin.getCurrencyManager().getBalanceSync(player.getUniqueId(), currencyId);
            return plugin.getConfigManager().formatAmount(balance, currency);
        }

        if (remaining.isEmpty() || remaining.equalsIgnoreCase("raw") || remaining.equalsIgnoreCase("balance_raw") || remaining.equalsIgnoreCase("raw_balance")) {
            if (player == null) return "0";
            double balance = plugin.getCurrencyManager().getBalanceSync(player.getUniqueId(), currencyId);
            if (!currency.isUseDecimals()) {
                return String.valueOf((long) Math.floor(balance));
            }
            return String.format("%." + plugin.getConfigManager().getDecimalPlaces() + "f", balance);
        }

        if (remaining.equalsIgnoreCase("symbol")) {
            return currency.getSymbol() != null ? currency.getSymbol() : "";
        }

        if (remaining.equalsIgnoreCase("displayname") || remaining.equalsIgnoreCase("name")) {
            return currency.getDisplayName();
        }

        if (remaining.equalsIgnoreCase("total_formatted") || remaining.equalsIgnoreCase("total_balance") || remaining.equalsIgnoreCase("total_amount")) {
            double total = plugin.getDatabaseManager().getTotalCurrencyValue(currencyId);
            return plugin.getConfigManager().formatAmount(total, currency);
        }

        if (remaining.equalsIgnoreCase("total") || remaining.equalsIgnoreCase("total_raw") || remaining.equalsIgnoreCase("total_balance_raw")) {
            double total = plugin.getDatabaseManager().getTotalCurrencyValue(currencyId);
            if (!currency.isUseDecimals()) {
                return String.valueOf((long) Math.floor(total));
            }
            return String.format("%." + plugin.getConfigManager().getDecimalPlaces() + "f", total);
        }

        if (remaining.toLowerCase().startsWith("top_")) {
            String[] topParts = remaining.split("_");
            if (topParts.length >= 2) {
                String type = "value";
                int position = -1;

                if (topParts.length == 2) {
                    try {
                        position = Integer.parseInt(topParts[1]);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else {
                    String p1 = topParts[1];
                    String p2 = topParts[2];
                    try {
                        position = Integer.parseInt(p1);
                        type = p2;
                    } catch (NumberFormatException e) {
                        try {
                            position = Integer.parseInt(p2);
                            type = p1;
                        } catch (NumberFormatException e2) {
                            return null;
                        }
                    }
                }

                updateTopCache(currencyId);
                List<LeaderboardEntry> topList = topCache.get(currencyId);

                if (topList == null || position < 1 || position > topList.size()) {
                    return type.equalsIgnoreCase("name") ? "---" : "0";
                }

                LeaderboardEntry entry = topList.get(position - 1);
                if (type.equalsIgnoreCase("name") || type.equalsIgnoreCase("player")) {
                    if (entry.getName() != null) return entry.getName();
                    OfflinePlayer topPlayer = Bukkit.getOfflinePlayer(entry.getUuid());
                    return topPlayer.getName() != null ? topPlayer.getName() : "Unknown";
                } else if (type.equalsIgnoreCase("value") || type.equalsIgnoreCase("formatted") || type.equalsIgnoreCase("balance") || type.equalsIgnoreCase("amount")) {
                    return plugin.getConfigManager().formatAmount(entry.getBalance(), currency);
                } else if (type.equalsIgnoreCase("rawvalue") || type.equalsIgnoreCase("raw") || type.equalsIgnoreCase("balance_raw")) {
                    if (!currency.isUseDecimals()) {
                        return String.valueOf((long) Math.floor(entry.getBalance()));
                    }
                    return String.format("%." + plugin.getConfigManager().getDecimalPlaces() + "f", entry.getBalance());
                }
            }
        }

        return null;
    }

    private void updateTopCache(String currencyId) {
        long now = System.currentTimeMillis();
        long cacheTime = plugin.getConfigManager().getTopCacheMinutes() * 60 * 1000L;

        if (now - lastCacheUpdate.getOrDefault(currencyId, 0L) < cacheTime && topCache.containsKey(currencyId)) {
            return;
        }

        List<LeaderboardEntry> topBalances = plugin.getDatabaseManager().getTopBalancesExtended(currencyId, 20);
        topCache.put(currencyId, topBalances);
        lastCacheUpdate.put(currencyId, now);
    }
}
