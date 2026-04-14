package net.busybee.obfeco.ui.impl;

import net.busybee.obfeco.Obfeco;
import net.busybee.obfeco.core.Currency;
import net.busybee.obfeco.ui.InventoryButton;
import net.busybee.obfeco.ui.InventoryGUI;
import net.busybee.obfeco.util.ColorUtil;
import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class CurrencyEditorGUI extends InventoryGUI {
    private final Obfeco plugin;
    private final Currency currency;

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, ColorUtil.colorizeToLegacy("<gold>Editing: " + currency.getId()));
    }

    @Override
    public void decorate(Player player) {
        this.addButton(10, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.NAME_TAG, "<yellow>Display Name", 
                List.of("<gray>Current: <white>" + currency.getDisplayName(), "", "<yellow>Click to change")))
            .consumer(event -> {
                player.closeInventory();
                plugin.getSignInput().open(player, new String[]{currency.getDisplayName(), "^^^^^^^^^^^^^^^", "Enter New", "Display Name"}, lines -> {
                    String newName = lines[0].trim();
                    if (!newName.isEmpty()) {
                        currency.setDisplayName(newName);
                        plugin.getCurrencyManager().saveCurrency(currency);
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <gray>Display name updated to: <white>" + newName));
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openGUI(new CurrencyEditorGUI(plugin, currency), player));
                });
            })
        );

        this.addButton(11, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.FEATHER, "<yellow>Symbol", 
                List.of("<gray>Current: <white>" + currency.getSymbol(), "", "<yellow>Click to change")))
            .consumer(event -> {
                player.closeInventory();
                plugin.getSignInput().open(player, new String[]{currency.getSymbol(), "^^^^^^^^^^^^^^^", "Enter New", "Symbol"}, lines -> {
                    String newSymbol = lines[0].trim();
                    currency.setSymbol(newSymbol);
                    plugin.getCurrencyManager().saveCurrency(currency);
                    player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <gray>Symbol updated to: <white>" + newSymbol));
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openGUI(new CurrencyEditorGUI(plugin, currency), player));
                });
            })
        );

        this.addButton(12, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.GOLD_INGOT, "<yellow>Starting Balance", 
                List.of("<gray>Current: <white>" + currency.getStartingBalance(), "", "<yellow>Click to change")))
            .consumer(event -> {
                player.closeInventory();
                plugin.getSignInput().open(player, new String[]{String.valueOf(currency.getStartingBalance()), "^^^^^^^^^^^^^^^", "Enter New", "Starting Balance"}, lines -> {
                    try {
                        double newBalance = Double.parseDouble(lines[0].trim());
                        currency.setStartingBalance(newBalance);
                        plugin.getCurrencyManager().saveCurrency(currency);
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <gray>Starting balance updated to: <white>" + newBalance));
                    } catch (NumberFormatException e) {
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Invalid number format."));
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openGUI(new CurrencyEditorGUI(plugin, currency), player));
                });
            })
        );

        this.addButton(13, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.BOOK, "<yellow>Format", 
                List.of("<gray>Current: <white>" + currency.getFormat(), "", "<yellow>Click to change")))
            .consumer(event -> {
                player.closeInventory();
                plugin.getSignInput().open(player, new String[]{currency.getFormat(), "^^^^^^^^^^^^^^^", "Enter New", "Format"}, lines -> {
                    String newFormat = lines[0].trim();
                    if (!newFormat.isEmpty()) {
                        currency.setFormat(newFormat);
                        plugin.getCurrencyManager().saveCurrency(currency);
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <gray>Format updated to: <white>" + newFormat));
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openGUI(new CurrencyEditorGUI(plugin, currency), player));
                });
            })
        );

        this.addButton(14, new InventoryButton()
            .creator(p -> createItem(p, currency.isUseDecimals() ? XMaterial.LIME_DYE : XMaterial.GRAY_DYE, 
                "<yellow>Use Decimals", 
                List.of("<gray>Status: " + (currency.isUseDecimals() ? "<green>Enabled" : "<red>Disabled"), "", "<yellow>Click to toggle")))
            .consumer(event -> {
                currency.setUseDecimals(!currency.isUseDecimals());
                plugin.getCurrencyManager().saveCurrency(currency);
                decorate(player);
            })
        );

        this.addButton(16, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.PAPER, "<yellow>Notifications", 
                List.of("<gray>Give: " + (currency.isNotifyGive() ? "<green>ON" : "<red>OFF"),
                        "<gray>Take: " + (currency.isNotifyTake() ? "<green>ON" : "<red>OFF"),
                        "", "<yellow>Click to toggle both")))
            .consumer(event -> {
                boolean newState = !currency.isNotifyGive();
                currency.setNotifyGive(newState);
                currency.setNotifyTake(newState);
                plugin.getCurrencyManager().saveCurrency(currency);
                decorate(player);
            })
        );

        this.addButton(22, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.ARROW, "<red>Back", List.of()))
            .consumer(event -> plugin.getGuiManager().openGUI(new CurrencyManagerGUI(plugin), player))
        );

        super.decorate(player);
    }

    private ItemStack createItem(Player player, XMaterial material, String name, List<String> lore) {
        ItemStack item = material.parseItem();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtil.colorizeToLegacy(player, name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) coloredLore.add(ColorUtil.colorizeToLegacy(player, line));
        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }
}
