package com.busybee.obfeco.ui.impl;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import com.busybee.obfeco.ui.InventoryButton;
import com.busybee.obfeco.ui.InventoryGUI;
import com.busybee.obfeco.util.ColorUtil;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.profiles.builder.XSkull;
import com.cryptomorin.xseries.profiles.objects.Profileable;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class TopBalancesGUI extends InventoryGUI {
    private final Obfeco plugin;
    private final Currency currency;
    private final int page;

    private static final int[] ENTRY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int PAGE_SIZE = ENTRY_SLOTS.length;

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 45,
            ColorUtil.colorizeToLegacy("<gold><bold>Top " + currency.getDisplayName() + " <gray>» Page " + page));
    }

    @Override
    public void decorate(Player player) {
        int offset = (page - 1) * PAGE_SIZE;
        int fetchLimit = offset + PAGE_SIZE;

        List<Map.Entry<UUID, Double>> topBalances = plugin.getDatabaseManager().getTopBalances(currency.getId(), fetchLimit);

        for (int i = 0; i < PAGE_SIZE; i++) {
            int dataIndex = offset + i;
            int slot = ENTRY_SLOTS[i];
            int rank = dataIndex + 1;

            if (dataIndex >= topBalances.size()) {
                this.addButton(slot, new InventoryButton()
                    .creator(p -> createEmptySlot())
                    .consumer(event -> {})
                );
                continue;
            }

            Map.Entry<UUID, Double> entry = topBalances.get(dataIndex);
            UUID entryUuid = entry.getKey();
            double balance = entry.getValue();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entryUuid);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
            String formattedBalance = plugin.getConfigManager().formatAmount(balance, currency);

            this.addButton(slot, new InventoryButton()
                .creator(p -> createPlayerHead(entryUuid, playerName, rank, formattedBalance))
                .consumer(event -> {
                    if (event.isLeftClick()) {
                        sendCopyMessage(player, playerName, entryUuid.toString(), rank, formattedBalance);
                    } else if (event.isRightClick()) {
                        sendCopyMessage(player, playerName, entryUuid.toString(), rank, formattedBalance);
                    }
                })
            );
        }

        boolean hasPrev = page > 1;
        boolean hasNext = topBalances.size() >= fetchLimit;

        this.addButton(38, new InventoryButton()
            .creator(p -> {
                if (!hasPrev) return createFillerPane();
                return createNavItem(XMaterial.ARROW, "<yellow>Previous Page", "<gray>Go to page " + (page - 1));
            })
            .consumer(event -> {
                if (hasPrev) {
                    plugin.getGuiManager().openGUI(new TopBalancesGUI(plugin, currency, page - 1), player);
                }
            })
        );

        this.addButton(40, new InventoryButton()
            .creator(p -> createNavItem(XMaterial.BARRIER, "<red>Close", "<gray>Close this menu"))
            .consumer(event -> player.closeInventory())
        );

        this.addButton(42, new InventoryButton()
            .creator(p -> {
                if (!hasNext) return createFillerPane();
                return createNavItem(XMaterial.ARROW, "<yellow>Next Page", "<gray>Go to page " + (page + 1));
            })
            .consumer(event -> {
                if (hasNext) {
                    plugin.getGuiManager().openGUI(new TopBalancesGUI(plugin, currency, page + 1), player);
                }
            })
        );

        super.decorate(player);
    }

    private void sendCopyMessage(Player player, String playerName, String uuid, int rank, String balance) {
        player.sendMessage(ColorUtil.colorizeToLegacy("<gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(ColorUtil.colorizeToLegacy("<gold><bold>#" + rank + " " + playerName));
        player.sendMessage(ColorUtil.colorizeToLegacy("<gray>Balance: <white>" + balance));

        TextComponent nameComponent = new TextComponent(ColorUtil.colorizeToLegacy("<yellow>► Click to copy IGN: <white>" + playerName));
        nameComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, playerName));
        nameComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(ColorUtil.colorizeToLegacy("<gray>Click to copy <white>" + playerName + " <gray>to chat")).create()));
        player.spigot().sendMessage(nameComponent);

        TextComponent uuidComponent = new TextComponent(ColorUtil.colorizeToLegacy("<yellow>► Click to copy UUID: <white>" + uuid));
        uuidComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid));
        uuidComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(ColorUtil.colorizeToLegacy("<gray>Click to copy UUID to chat")).create()));
        player.spigot().sendMessage(uuidComponent);

        player.sendMessage(ColorUtil.colorizeToLegacy("<gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    private ItemStack createPlayerHead(UUID uuid, String playerName, int rank, String balance) {
        ItemStack head;
        try {
            head = XSkull.createItem().profile(Profileable.detect(playerName)).apply();
        } catch (Exception e) {
            head = XMaterial.matchXMaterial("PLAYER_HEAD").map(XMaterial::parseItem).orElse(XMaterial.STONE.parseItem());
        }

        ItemMeta meta = head.getItemMeta();
        meta.setDisplayName(ColorUtil.colorizeToLegacy("<gold>#" + rank + " <white>" + playerName));

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.colorizeToLegacy("<gray>Balance: <yellow>" + balance));
        lore.add(ColorUtil.colorizeToLegacy("<gray>UUID: <white>" + uuid));
        lore.add("");
        lore.add(ColorUtil.colorizeToLegacy("<yellow>Left-Click <gray>to copy IGN"));
        lore.add(ColorUtil.colorizeToLegacy("<yellow>Right-Click <gray>to copy UUID"));
        meta.setLore(lore);

        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createEmptySlot() {
        ItemStack item = XMaterial.matchXMaterial("GRAY_STAINED_GLASS_PANE").map(XMaterial::parseItem).orElse(XMaterial.STONE.parseItem());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFillerPane() {
        ItemStack item = XMaterial.matchXMaterial("BLACK_STAINED_GLASS_PANE").map(XMaterial::parseItem).orElse(XMaterial.STONE.parseItem());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(XMaterial material, String name, String loreText) {
        ItemStack item = material.parseItem();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtil.colorizeToLegacy(name));
        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.colorizeToLegacy(loreText));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}