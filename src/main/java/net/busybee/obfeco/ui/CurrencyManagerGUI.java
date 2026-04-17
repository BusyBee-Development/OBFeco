package net.busybee.obfeco.ui;

import fr.mrmicky.fastinv.FastInv;
import net.busybee.obfeco.Obfeco;
import net.busybee.obfeco.core.Currency;
import net.busybee.obfeco.util.ColorUtil;
import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import net.busybee.obfeco.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class CurrencyManagerGUI extends FastInv {

    private final Obfeco plugin;

    public CurrencyManagerGUI(Obfeco plugin) {
        super(54, ColorUtil.colorizeToLegacy(
                plugin.getMessageManager().getMessage("gui.currency-manager.title")
        ));

        this.plugin = plugin;
    }

    @Override
    public void open(Player player) {
        refresh(player);
        super.open(player);
    }

    private void refresh(Player player) {
        clearItems();

        List<Currency> currencies = new ArrayList<>(plugin.getCurrencyManager().getCurrencies());

        for (int slot = 0; slot < Math.min(currencies.size(), 45); slot++) {
            Currency currency = currencies.get(slot);

            setItem(slot, createCurrencyItem(player, currency), event -> {
                if (event.isLeftClick()) {
                    new CurrencyEditorGUI(plugin, currency).open(player);
                    return;
                }

                if (!event.isRightClick()) {
                    return;
                }

                if (currency.getId().equalsIgnoreCase(plugin.getConfigManager().getPrimaryCurrency())) {
                    player.sendMessage(ColorUtil.colorize(
                            plugin.getMessageManager().getPrefix() + " " +
                                    plugin.getMessageManager().getMessage("currency.cannot-delete-primary")
                    ));
                    return;
                }

                plugin.getCurrencyManager().removeCurrency(currency.getId());
                plugin.getDatabaseManager().deleteCurrencyTable(currency.getId());

                player.sendMessage(ColorUtil.colorize(
                        plugin.getMessageManager().getPrefix() + " " +
                                plugin.getMessageManager().getMessage("currency.deleted")
                                        .replace("{currency}", currency.getDisplayName())
                ));

                refresh(player);
            });
        }

        setItem(49, createSimpleItem(
                player,
                XMaterial.EMERALD,
                plugin.getMessageManager().getMessage("gui.currency-manager.create-button"),
                plugin.getConfig().getStringList("gui.currency-manager.create-lore")
        ), event -> {
            player.closeInventory();

            player.sendMessage(ColorUtil.colorize(
                    plugin.getMessageManager().getPrefix() +
                            " <gray>Please enter the <yellow>Currency Name <gray>on the sign."
            ));

            plugin.getSignInput().open(
                    player,
                    new String[]{"", "^^^^^^^^^^^^^^^", "Enter Currency", "Name Above"},
                    lines -> {
                        String displayName = lines[0].trim();

                        if (displayName.isEmpty()) {
                            player.sendMessage(ColorUtil.colorize(
                                    plugin.getMessageManager().getPrefix() +
                                            " <red>Creation cancelled: Name cannot be empty."
                            ));
                            return;
                        }

                        String id = displayName
                                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                                .replaceAll("[^a-zA-Z0-9_]", "")
                                .toLowerCase();

                        if (id.isEmpty()) {
                            player.sendMessage(ColorUtil.colorize(
                                    plugin.getMessageManager().getPrefix() +
                                            " <red>Creation cancelled: Name must contain alphanumeric characters."
                            ));
                            return;
                        }

                        if (plugin.getCurrencyManager().getCurrency(id) != null) {
                            player.sendMessage(ColorUtil.colorize(
                                    plugin.getMessageManager().getPrefix() + " " +
                                            plugin.getMessageManager().getMessage("currency.already-exists")
                                                    .replace("{currency}", id)
                            ));
                            return;
                        }

                        Currency currency = new Currency(
                                id,
                                displayName,
                                "",
                                "%amount%%symbol%",
                                "GOLD_INGOT",
                                0.0,
                                true,
                                true,
                                true
                        );

                        plugin.getCurrencyManager().addCurrency(currency);
                        plugin.getDatabaseManager().createCurrencyTable(id);

                        player.sendMessage(ColorUtil.colorize(
                                plugin.getMessageManager().getPrefix() + " " +
                                        plugin.getMessageManager().getMessage("currency.created")
                                                .replace("{currency}", displayName)
                        ));

                        FoliaUtil.run(plugin, () ->
                                new CurrencyManagerGUI(plugin).open(player)
                        );
                    }
            );
        });

        setItem(53, createSimpleItem(
                player,
                XMaterial.BARRIER,
                plugin.getMessageManager().getMessage("gui.currency-manager.close-button"),
                List.of()
        ), event -> player.closeInventory());
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

    private ItemStack createSimpleItem(Player player, XMaterial material, String name, List<String> loreLines) {
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
