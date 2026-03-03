package com.busybee.obfeco.ui.impl;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import com.busybee.obfeco.ui.InventoryButton;
import com.busybee.obfeco.ui.InventoryGUI;
import com.busybee.obfeco.util.ColorUtil;
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
public class CurrencyManagerGUI extends InventoryGUI {
    private final Obfeco plugin;
    
    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, ColorUtil.colorizeToLegacy(plugin.getMessageManager().getMessage("gui.currency-manager.title")));
    }
    
    @Override
    public void decorate(Player player) {
        List<Currency> currencies = new ArrayList<>(plugin.getCurrencyManager().getCurrencies());
        
        for (int i = 0; i < Math.min(currencies.size(), 45); i++) {
            Currency currency = currencies.get(i);
            
            this.addButton(i, new InventoryButton()
                .creator(p -> createCurrencyItem(p, currency))
                .consumer(event -> {
                    if (event.isLeftClick()) {
                        plugin.getGuiManager().openGUI(new CurrencyEditorGUI(plugin, currency), player);
                    } else if (event.isRightClick()) {
                        if (currency.getId().equals(plugin.getConfigManager().getPrimaryCurrency())) {
                            player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " + 
                                plugin.getMessageManager().getMessage("currency.cannot-delete-primary")));
                            return;
                        }
                        
                        plugin.getCurrencyManager().removeCurrency(currency.getId());
                        plugin.getDatabaseManager().deleteCurrencyTable(currency.getId());
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " + 
                            plugin.getMessageManager().getMessage("currency.deleted").replace("{currency}", currency.getDisplayName())));
                        decorate(player);
                    }
                })
            );
        }
        
        this.addButton(49, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.EMERALD, 
                plugin.getMessageManager().getMessage("gui.currency-manager.create-button"),
                plugin.getConfig().getStringList("gui.currency-manager.create-lore")))
            .consumer(event -> {
                player.closeInventory();
                
                player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <gray>Please enter the <yellow>Currency Name <gray>on the sign."));

                plugin.getSignInput().open(player, new String[]{"", "^^^^^^^^^^^^^^^", "Enter Currency", "Name Above"}, lines -> {
                    String name = lines[0].trim();
                    if (name.isEmpty()) {
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Creation cancelled: Name cannot be empty."));
                        return;
                    }

                    String id = name.replaceAll("(?i)§[0-9A-FK-ORX]", "")
                                    .replaceAll("[^a-zA-Z0-9_]", "")
                                    .toLowerCase();
                    
                    if (id.isEmpty()) {
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Creation cancelled: Name must contain alphanumeric characters."));
                        return;
                    }
                    
                    if (plugin.getCurrencyManager().getCurrency(id) != null) {
                        player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " +
                            plugin.getMessageManager().getMessage("currency.already-exists").replace("{currency}", id)));
                        return;
                    }

                    Currency newCurrency = new Currency(id, name, "", "%amount%%symbol%", "GOLD_INGOT", 0.0, true, true, true);
                    plugin.getCurrencyManager().addCurrency(newCurrency);
                    plugin.getDatabaseManager().createCurrencyTable(id);
                    
                    player.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " + 
                        plugin.getMessageManager().getMessage("currency.created").replace("{currency}", name)));

                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getGuiManager().openGUI(new CurrencyManagerGUI(plugin), player));
                });
            })
        );
        
        this.addButton(53, new InventoryButton()
            .creator(p -> createItem(p, XMaterial.BARRIER, 
                plugin.getMessageManager().getMessage("gui.currency-manager.close-button"),
                new ArrayList<>()))
            .consumer(event -> event.getWhoClicked().closeInventory())
        );
        
        super.decorate(player);
    }
    
    private ItemStack createCurrencyItem(Player player, Currency currency) {
        ItemStack item = XMaterial.GOLD_INGOT.parseItem();
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ColorUtil.colorizeToLegacy(player, currency.getDisplayName()));
        
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("gui.currency-manager.currency-lore")) {
            lore.add(ColorUtil.colorizeToLegacy(player, line
                .replace("{id}", currency.getId())
                .replace("{display}", currency.getDisplayName())
                .replace("{starting}", String.valueOf(currency.getStartingBalance()))
                .replace("{notify-give}", String.valueOf(currency.isNotifyGive()))
                .replace("{notify-take}", String.valueOf(currency.isNotifyTake()))));
        }
        meta.setLore(lore);
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createItem(Player player, XMaterial material, String name, List<String> lore) {
        ItemStack item = material.parseItem();
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ColorUtil.colorizeToLegacy(player, name));
        
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ColorUtil.colorizeToLegacy(player, line));
        }
        meta.setLore(coloredLore);
        
        item.setItemMeta(meta);
        return item;
    }
}
