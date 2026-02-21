package com.busybee.obfeco.commands;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import com.busybee.obfeco.ui.impl.CurrencyManagerGUI;
import com.busybee.obfeco.util.ColorUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public enum SubCommandType {

    BALANCE("obfeco.balance", new String[]{"bal"},
        (plugin, sender, args) -> {
            if (args.length == 0) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Console must specify player and currency."));
                    return;
                }

                Player player = (Player) sender;
                String primaryCurrency = plugin.getConfigManager().getPrimaryCurrency();

                plugin.getCurrencyManager().getBalance(player.getUniqueId(), primaryCurrency).thenAccept(balance -> {
                    Currency currency = plugin.getCurrencyManager().getCurrency(primaryCurrency);
                    if (currency == null) {
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                            plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", primaryCurrency)));
                        return;
                    }

                    player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                        plugin.getMessageManager().getMessage("balance.own")
                            .replace("{currency}", currency.getDisplayName())
                            .replace("{amount}", plugin.getConfigManager().formatAmount(balance, currency))));
                });
                return;
            }

            if (args.length == 1) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Console must specify player and currency."));
                    return;
                }

                Player player = (Player) sender;
                String currencyId = args[0];
                Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

                if (currency == null) {
                    player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                        plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                    return;
                }

                plugin.getCurrencyManager().getBalance(player.getUniqueId(), currencyId).thenAccept(balance -> {
                    player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                        plugin.getMessageManager().getMessage("balance.own")
                            .replace("{currency}", currency.getDisplayName())
                            .replace("{amount}", plugin.getConfigManager().formatAmount(balance, currency))));
                });
                return;
            }

            if (!sender.hasPermission("obfeco.balance.others")) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("no-permission")));
                return;
            }

            String targetName = args[0];
            String currencyId = args[1];

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            plugin.getCurrencyManager().getBalance(target.getUniqueId(), currencyId).thenAccept(balance -> {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("balance.others")
                        .replace("{player}", target.getName() != null ? target.getName() : targetName)
                        .replace("{currency}", currency.getDisplayName())
                        .replace("{amount}", plugin.getConfigManager().formatAmount(balance, currency))));
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                if (sender.hasPermission("obfeco.balance.others")) {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
                }
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 2 && sender.hasPermission("obfeco.balance.others")) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            return Collections.emptyList();
        }),

    PAY("obfeco.pay", new String[]{},
        (plugin, sender, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>This command can only be used by players."));
                return;
            }

            if (args.length < 3) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco pay <player> <currency> <amount>"));
                return;
            }

            Player player = (Player) sender;
            String targetName = args[0];
            String currencyId = args[1];

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            if (amount <= 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("player-not-found").replace("{player}", targetName)));
                return;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>You cannot pay yourself."));
                return;
            }

            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            double finalAmount = amount;
            plugin.getCurrencyManager().getBalance(player.getUniqueId(), currencyId).thenAccept(balance -> {
                if (balance < finalAmount) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                            plugin.getMessageManager().getMessage("insufficient-funds").replace("{currency}", currency.getDisplayName()))));
                    return;
                }

                plugin.getCurrencyManager().removeBalance(player.getUniqueId(), currencyId, finalAmount, true).thenAccept(removed -> {
                    if (removed) {
                        plugin.getCurrencyManager().addBalance(target.getUniqueId(), currencyId, finalAmount, true).thenAccept(added -> {
                            if (added) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                                        plugin.getMessageManager().getMessage("transaction.pay-sent")
                                            .replace("{player}", target.getName())
                                            .replace("{amount}", plugin.getConfigManager().formatAmount(finalAmount, currency))
                                            .replace("{currency}", currency.getDisplayName())));

                                    target.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                                        plugin.getMessageManager().getMessage("transaction.pay-received")
                                            .replace("{player}", player.getName())
                                            .replace("{amount}", plugin.getConfigManager().formatAmount(finalAmount, currency))
                                            .replace("{currency}", currency.getDisplayName())));
                                });

                                if (plugin.getConfigManager().isUserTransactions()) {
                                    plugin.getLogManager().logTransaction(player.getName(), target.getName(), currencyId, "paid", finalAmount);
                                }
                            }
                        });
                    }
                });
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 2) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            return Collections.emptyList();
        }),

    TOP("obfeco.top", new String[]{},
        (plugin, sender, args) -> {
            if (args.length == 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco top <currency> [page]"));
                return;
            }

            String currencyId = args[0];
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            int page = 1;
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {}
            }

            int finalPage = Math.max(1, page);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                int limit = 10;
                int offset = (finalPage - 1) * limit;
                List<Map.Entry<UUID, Double>> topBalances = plugin.getDatabaseManager().getTopBalances(currencyId, limit + offset);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getMessage("top.header")
                        .replace("{currency}", currency.getDisplayName())));

                    for (int i = offset; i < Math.min(topBalances.size(), offset + limit); i++) {
                        Map.Entry<UUID, Double> entry = topBalances.get(i);
                        OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getMessage("top.entry")
                            .replace("{position}", String.valueOf(i + 1))
                            .replace("{player}", player.getName() != null ? player.getName() : "Unknown")
                            .replace("{amount}", plugin.getConfigManager().formatAmount(entry.getValue(), currency))));
                    }

                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getMessage("top.footer")));
                });
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }),

    GIVE("obfeco.give", new String[]{},
        (plugin, sender, args) -> {
            if (args.length < 3) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco give <player> <currency> <amount> [-s]"));
                return;
            }

            String targetName = args[0];
            String currencyId = args[1];

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            if (amount <= 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            boolean silent = args.length > 3 && args[3].equalsIgnoreCase("-s") && sender.hasPermission("obfeco.silent");

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            final String resolvedTargetName = target.getName() != null ? target.getName() : targetName;
            plugin.getCurrencyManager().addBalance(target.getUniqueId(), currencyId, amount, silent).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                            plugin.getMessageManager().getMessage("transaction.give")
                                .replace("{player}", resolvedTargetName)
                                .replace("{amount}", plugin.getConfigManager().formatAmount(amount, currency))
                                .replace("{currency}", currency.getDisplayName())));

                        if (plugin.getConfigManager().isAdminTransactions()) {
                            plugin.getLogManager().logTransaction(sender.getName(), resolvedTargetName, currencyId, "gave", amount);
                        }
                    } else {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Failed to give currency."));
                    }
                });
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 2) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 4 && sender.hasPermission("obfeco.silent")) {
                return Collections.singletonList("-s");
            }

            return Collections.emptyList();
        }),

    TAKE("obfeco.take", new String[]{},
        (plugin, sender, args) -> {
            if (args.length < 3) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco take <player> <currency> <amount> [-s]"));
                return;
            }

            String targetName = args[0];
            String currencyId = args[1];

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            if (amount <= 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            boolean silent = args.length > 3 && args[3].equalsIgnoreCase("-s") && sender.hasPermission("obfeco.silent");

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            final String resolvedTargetName = target.getName() != null ? target.getName() : targetName;
            plugin.getCurrencyManager().removeBalance(target.getUniqueId(), currencyId, amount, silent).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                            plugin.getMessageManager().getMessage("transaction.take")
                                .replace("{player}", resolvedTargetName)
                                .replace("{amount}", plugin.getConfigManager().formatAmount(amount, currency))
                                .replace("{currency}", currency.getDisplayName())));

                        if (plugin.getConfigManager().isAdminTransactions()) {
                            plugin.getLogManager().logTransaction(sender.getName(), resolvedTargetName, currencyId, "took", amount);
                        }
                    } else {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                            plugin.getMessageManager().getMessage("insufficient-funds").replace("{currency}", currency.getDisplayName())));
                    }
                });
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 2) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 4 && sender.hasPermission("obfeco.silent")) {
                return Collections.singletonList("-s");
            }

            return Collections.emptyList();
        }),

    SET("obfeco.set", new String[]{},
        (plugin, sender, args) -> {
            if (args.length < 3) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco set <player> <currency> <amount> [-s]"));
                return;
            }

            String targetName = args[0];
            String currencyId = args[1];

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            if (amount < 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("invalid-amount")));
                return;
            }

            boolean silent = args.length > 3 && args[3].equalsIgnoreCase("-s") && sender.hasPermission("obfeco.silent");

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            final String resolvedTargetName = target.getName() != null ? target.getName() : targetName;
            plugin.getCurrencyManager().setBalance(target.getUniqueId(), currencyId, amount).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                            plugin.getMessageManager().getMessage("transaction.set")
                                .replace("{player}", resolvedTargetName)
                                .replace("{amount}", plugin.getConfigManager().formatAmount(amount, currency))
                                .replace("{currency}", currency.getDisplayName())));

                        if (plugin.getConfigManager().isAdminTransactions()) {
                            plugin.getLogManager().logTransaction(sender.getName(), resolvedTargetName, currencyId, "set", amount);
                        }
                    } else {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Failed to set balance."));
                    }
                });
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 2) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 4 && sender.hasPermission("obfeco.silent")) {
                return Collections.singletonList("-s");
            }

            return Collections.emptyList();
        }),

    CREATE("obfeco.create", new String[]{},
        (plugin, sender, args) -> {
            if (args.length < 2) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco create <id> <display-name> [starting-balance] [use-decimals]"));
                return;
            }

            String id = args[0].toLowerCase();
            String displayName = args[1];
            double startingBalance = 0.0;
            boolean useDecimals = true;

            if (args.length > 2) {
                try {
                    startingBalance = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                        plugin.getMessageManager().getMessage("invalid-amount")));
                    return;
                }
            }

            if (args.length > 3) {
                useDecimals = Boolean.parseBoolean(args[3]);
            }

            if (plugin.getCurrencyManager().getCurrency(id) != null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency.already-exists").replace("{currency}", id)));
                return;
            }

            Currency currency = new Currency(id, displayName, "", "%amount%%symbol%", "GOLD_INGOT", startingBalance, useDecimals, true, true);
            plugin.getCurrencyManager().addCurrency(currency);
            plugin.getDatabaseManager().createCurrencyTable(id);

            sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                plugin.getMessageManager().getMessage("currency.created").replace("{currency}", displayName)));
        },
        (plugin, sender, args) -> {
            if (args.length == 4) {
                return Arrays.asList("true", "false");
            }
            return Collections.emptyList();
        }),

    DELETE("obfeco.delete", new String[]{},
        (plugin, sender, args) -> {
            if (args.length == 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco delete <currency>"));
                return;
            }

            String currencyId = args[0];
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            if (currencyId.equals(plugin.getConfigManager().getPrimaryCurrency())) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency.cannot-delete-primary")));
                return;
            }

            plugin.getCurrencyManager().removeCurrency(currencyId);
            plugin.getDatabaseManager().deleteCurrencyTable(currencyId);

            sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                plugin.getMessageManager().getMessage("currency.deleted").replace("{currency}", currency.getDisplayName())));
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }),

    RESET("obfeco.reset", new String[]{},
        (plugin, sender, args) -> {
            if (args.length == 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco reset <currency> [confirm]"));
                return;
            }

            String currencyId = args[0];
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency-not-found").replace("{currency}", currencyId)));
                return;
            }

            if (args.length == 1) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency.reset-confirm").replace("{currency}", currencyId)));
                return;
            }

            if (args[1].equalsIgnoreCase("confirm")) {
                plugin.getDatabaseManager().resetCurrency(currencyId);
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                    plugin.getMessageManager().getMessage("currency.reset-success").replace("{currency}", currency.getDisplayName())));
            }
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return plugin.getCurrencyManager().getCurrencies().stream()
                    .map(Currency::getId)
                    .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (args.length == 2) {
                return Collections.singletonList("confirm");
            }

            return Collections.emptyList();
        }),

    SCAN("obfeco.admin", new String[]{},
        (plugin, sender, args) -> {
            if (args.length == 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco scan <plugin>"));
                sender.sendMessage(ColorUtil.colorize("<gray>Available: <white>coinsengine"));
                return;
            }

            String source = args[0].toLowerCase();
            if (!source.equals("coinsengine")) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Unknown plugin: " + source));
                sender.sendMessage(ColorUtil.colorize("<gray>Available: <white>coinsengine"));
                return;
            }

            sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <yellow>Scanning for " + source + " currencies..."));

            com.busybee.obfeco.migration.MigrationManager migrationManager = new com.busybee.obfeco.migration.MigrationManager(plugin);
            migrationManager.scanCoinsEngine((success, currencies, error) -> {
                if (success) {
                    if (currencies.isEmpty()) {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <yellow>No currencies found in database."));
                        sender.sendMessage(ColorUtil.colorize("<gray>Make sure the database path and table name are correct in config.yml"));
                    } else {
                        sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <green>Found " + currencies.size() + " currencies:"));
                        for (String currency : currencies) {
                            sender.sendMessage(ColorUtil.colorize("  <gray>- <white>" + currency));
                        }
                        sender.sendMessage(ColorUtil.colorize("<gray>Configure mappings in config.yml, then use <white>/obfeco convert coinsengine"));
                    }
                } else {
                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Scan failed: " + error));
                }
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return Collections.singletonList("coinsengine");
            }
            return Collections.emptyList();
        }),

    CONVERT("obfeco.convert", new String[]{"migrate"},
        (plugin, sender, args) -> {
            if (args.length == 0) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Usage: /obfeco convert <plugin>"));
                sender.sendMessage(ColorUtil.colorize("<gray>Available: <white>coinsengine"));
                sender.sendMessage(ColorUtil.colorize("<yellow>Tip: Use <white>/obfeco scan coinsengine</white> first to see available currencies"));
                return;
            }

            String source = args[0].toLowerCase();
            if (!source.equals("coinsengine")) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Unknown plugin: " + source));
                sender.sendMessage(ColorUtil.colorize("<gray>Available: <white>coinsengine"));
                return;
            }

            sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                plugin.getMessageManager().getMessage("convert.started")));

            com.busybee.obfeco.migration.MigrationManager migrationManager = new com.busybee.obfeco.migration.MigrationManager(plugin);
            migrationManager.migrate(source, null, (success, count, error) -> {
                if (success) {
                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <green>Migration complete!"));
                    sender.sendMessage(ColorUtil.colorize("<gray>Total balances migrated: <white>" + count));
                    if (!error.isEmpty()) {
                        sender.sendMessage(ColorUtil.colorize("<yellow>" + error));
                    }
                } else {
                    sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                        plugin.getMessageManager().getMessage("convert.failed").replace("{error}", error)));
                }
            });
        },
        (plugin, sender, args) -> {
            if (args.length == 1) {
                return Collections.singletonList("coinsengine");
            }
            return Collections.emptyList();
        }),

    RELOAD("obfeco.reload", new String[]{},
        (plugin, sender, args) -> {
            plugin.reload();
            sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                plugin.getMessageManager().getMessage("reload.success")));
        },
        (plugin, sender, args) -> Collections.emptyList()),

    GUI("obfeco.gui", new String[]{"manage"},
        (plugin, sender, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>This command can only be used by players."));
                return;
            }

            Player player = (Player) sender;
            CurrencyManagerGUI gui = new CurrencyManagerGUI(plugin);
            plugin.getGuiManager().openGUI(gui, player);
        },
        (plugin, sender, args) -> Collections.emptyList());

    private final String permission;
    private final String[] aliases;
    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    SubCommandType(String permission, String[] aliases, CommandExecutor executor, TabCompleter tabCompleter) {
        this.permission = permission;
        this.aliases = aliases;
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    public void execute(Obfeco plugin, CommandSender sender, String[] args) {
        executor.execute(plugin, sender, args);
    }

    public List<String> tabComplete(Obfeco plugin, CommandSender sender, String[] args) {
        return tabCompleter.complete(plugin, sender, args);
    }

    public static SubCommandType fromString(String name) {
        for (SubCommandType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
            for (String alias : type.aliases) {
                if (alias.equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }
        return null;
    }

    @FunctionalInterface
    interface CommandExecutor {
        void execute(Obfeco plugin, CommandSender sender, String[] args);
    }

    @FunctionalInterface
    interface TabCompleter {
        List<String> complete(Obfeco plugin, CommandSender sender, String[] args);
    }
}