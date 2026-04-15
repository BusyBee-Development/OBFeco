package net.busybee.obfeco.ui;

import fr.mrmicky.fastinv.FastInv;
import net.busybee.obfeco.Obfeco;
import net.busybee.obfeco.core.Currency;
import net.busybee.obfeco.util.ColorUtil;
import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class CurrencyEditorGUI extends FastInv {

    private final Obfeco plugin;
    private final Currency currency;

    public CurrencyEditorGUI(Obfeco plugin, Currency currency) {
        super(27, ColorUtil.colorizeToLegacy("<gold>Editing: " + currency.getId()));

        this.plugin = plugin;
        this.currency = currency;
    }

    @Override
    public void open(Player player) {
        refresh(player);
        super.open(player);
    }

    private void refresh(Player player) {
        clearItems();

        setItem(10, createItem(
                player,
                XMaterial.NAME_TAG,
                "<yellow>Display Name",
                List.of(
                        "<gray>Current: <white>" + currency.getDisplayName(),
                        "",
                        "<yellow>Click to change"
                )
        ), event -> {
            player.closeInventory();

            plugin.getSignInput().open(
                    player,
                    new String[]{
                            currency.getDisplayName(),
                            "^^^^^^^^^^^^^^^",
                            "Enter New",
                            "Display Name"
                    },
                    lines -> {
                        String value = lines[0].trim();

                        if (!value.isEmpty()) {
                            currency.setDisplayName(value);
                            plugin.getCurrencyManager().saveCurrency(currency);

                            player.sendMessage(ColorUtil.colorize(
                                    plugin.getMessageManager().getPrefix() +
                                            " <gray>Display name updated to: <white>" + value
                            ));
                        }

                        reopen(player);
                    }
            );
        });

        setItem(11, createItem(
                player,
                XMaterial.FEATHER,
                "<yellow>Symbol",
                List.of(
                        "<gray>Current: <white>" + currency.getSymbol(),
                        "",
                        "<yellow>Click to change"
                )
        ), event -> {
            player.closeInventory();

            plugin.getSignInput().open(
                    player,
                    new String[]{
                            currency.getSymbol(),
                            "^^^^^^^^^^^^^^^",
                            "Enter New",
                            "Symbol"
                    },
                    lines -> {
                        String value = lines[0].trim();

                        currency.setSymbol(value);
                        plugin.getCurrencyManager().saveCurrency(currency);

                        player.sendMessage(ColorUtil.colorize(
                                plugin.getMessageManager().getPrefix() +
                                        " <gray>Symbol updated to: <white>" + value
                        ));

                        reopen(player);
                    }
            );
        });

        setItem(12, createItem(
                player,
                XMaterial.GOLD_INGOT,
                "<yellow>Starting Balance",
                List.of(
                        "<gray>Current: <white>" + currency.getStartingBalance(),
                        "",
                        "<yellow>Click to change"
                )
        ), event -> {
            player.closeInventory();

            plugin.getSignInput().open(
                    player,
                    new String[]{
                            String.valueOf(currency.getStartingBalance()),
                            "^^^^^^^^^^^^^^^",
                            "Enter New",
                            "Starting Balance"
                    },
                    lines -> {
                        try {
                            double value = Double.parseDouble(lines[0].trim());

                            currency.setStartingBalance(value);
                            plugin.getCurrencyManager().saveCurrency(currency);

                            player.sendMessage(ColorUtil.colorize(
                                    plugin.getMessageManager().getPrefix() +
                                            " <gray>Starting balance updated to: <white>" + value
                            ));
                        } catch (NumberFormatException exception) {
                            player.sendMessage(ColorUtil.colorize(
                                    plugin.getMessageManager().getPrefix() +
                                            " <red>Invalid number format."
                            ));
                        }

                        reopen(player);
                    }
            );
        });

        setItem(13, createItem(
                player,
                XMaterial.BOOK,
                "<yellow>Format",
                List.of(
                        "<gray>Current: <white>" + currency.getFormat(),
                        "",
                        "<yellow>Click to change"
                )
        ), event -> {
            player.closeInventory();

            plugin.getSignInput().open(
                    player,
                    new String[]{
                            currency.getFormat(),
                            "^^^^^^^^^^^^^^^",
                            "Enter New",
                            "Format"
                    },
                    lines -> {
                        String value = lines[0].trim();

                        if (!value.isEmpty()) {
                            currency.setFormat(value);
                            plugin.getCurrencyManager().saveCurrency(currency);

                            player.sendMessage(ColorUtil.colorize(
                                    plugin.getMessageManager().getPrefix() +
                                            " <gray>Format updated to: <white>" + value
                            ));
                        }

                        reopen(player);
                    }
            );
        });

        setItem(14, createItem(
                player,
                currency.isUseDecimals() ? XMaterial.LIME_DYE : XMaterial.GRAY_DYE,
                "<yellow>Use Decimals",
                List.of(
                        "<gray>Status: " + (currency.isUseDecimals()
                                ? "<green>Enabled"
                                : "<red>Disabled"),
                        "",
                        "<yellow>Click to toggle"
                )
        ), event -> {
            currency.setUseDecimals(!currency.isUseDecimals());
            plugin.getCurrencyManager().saveCurrency(currency);

            refresh(player);
        });

        setItem(15, createItem(
                player,
                currency.isNotifyGive() ? XMaterial.BELL : XMaterial.NOTE_BLOCK,
                "<yellow>Give Notifications",
                List.of(
                        "<gray>Status: " + (currency.isNotifyGive()
                                ? "<green>Enabled"
                                : "<red>Disabled"),
                        "",
                        "<yellow>Click to toggle"
                )
        ), event -> {
            currency.setNotifyGive(!currency.isNotifyGive());
            plugin.getCurrencyManager().saveCurrency(currency);

            refresh(player);
        });

        setItem(16, createItem(
                player,
                currency.isNotifyTake() ? XMaterial.BELL : XMaterial.NOTE_BLOCK,
                "<yellow>Take Notifications",
                List.of(
                        "<gray>Status: " + (currency.isNotifyTake()
                                ? "<green>Enabled"
                                : "<red>Disabled"),
                        "",
                        "<yellow>Click to toggle"
                )
        ), event -> {
            currency.setNotifyTake(!currency.isNotifyTake());
            plugin.getCurrencyManager().saveCurrency(currency);

            refresh(player);
        });

        setItem(22, createItem(
                player,
                XMaterial.ARROW,
                "<red>Back",
                List.of("<gray>Return to currency manager")
        ), event -> new CurrencyManagerGUI(plugin).open(player));
    }

    private void reopen(Player player) {
        Bukkit.getScheduler().runTask(plugin, () ->
                new CurrencyEditorGUI(plugin, currency).open(player)
        );
    }

    private ItemStack createItem(
            Player player,
            XMaterial material,
            String name,
            List<String> loreLines
    ) {
        ItemStack item = material.parseItem();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorUtil.colorizeToLegacy(player, name));

        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ColorUtil.colorizeToLegacy(player, line));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }
}