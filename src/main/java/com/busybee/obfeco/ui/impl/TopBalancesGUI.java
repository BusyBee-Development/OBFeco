package com.busybee.obfeco.ui.impl;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.core.Currency;
import com.busybee.obfeco.database.DatabaseManager;
import com.busybee.obfeco.ui.InventoryButton;
import com.busybee.obfeco.ui.InventoryGUI;
import com.busybee.obfeco.util.ColorUtil;
import com.cryptomorin.xseries.XMaterial;
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
import java.util.UUID;

@RequiredArgsConstructor
public class TopBalancesGUI extends InventoryGUI {
    private final Obfeco plugin;
    private final Currency currency;
    private final int page;

    private List<DatabaseManager.LeaderboardEntry> cachedData = null;
    private boolean loading = false;

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

        if (cachedData == null && !loading) {
            loading = true;
            // Fill with loading state
            for (int slot : ENTRY_SLOTS) {
                this.addButton(slot, new InventoryButton()
                    .creator(p -> createLoadingItem())
                    .consumer(event -> {})
                );
            }
            
            plugin.getDatabaseManager().getTopBalancesExtendedAsync(currency.getId(), fetchLimit).thenAccept(data -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    this.cachedData = data;
                    this.loading = false;
                    if (player.getOpenInventory().getTopInventory().equals(getInventory())) {
                        this.decorate(player);
                    }
                });
            });
        }

        List<DatabaseManager.LeaderboardEntry> topBalances = cachedData != null ? cachedData : new ArrayList<>();

        for (int i = 0; i < PAGE_SIZE; i++) {
            int dataIndex = offset + i;
            int slot = ENTRY_SLOTS[i];
            int rank = dataIndex + 1;

            if (dataIndex >= topBalances.size()) {
                if (!loading) {
                    this.addButton(slot, new InventoryButton()
                        .creator(p -> createEmptySlot())
                        .consumer(event -> {})
                    );
                }
                continue;
            }

            DatabaseManager.LeaderboardEntry entry = topBalances.get(dataIndex);
            UUID entryUuid = entry.getUuid();
            double balance = entry.getBalance();
            String cachedName = entry.getName();
            
            this.addButton(slot, new InventoryButton()
                .creator(p -> {
                    String pName = entry.getName();
                    if (pName == null) {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entryUuid);
                        pName = offlinePlayer.getName();
                    }
                    if (pName == null) pName = "Unknown";
                    
                    String fBalance = plugin.getConfigManager().formatAmount(balance, currency);
                    return createPlayerHead(p, entryUuid, pName, rank, fBalance);
                })
                .consumer(event -> {
                    String playerName = cachedName;
                    if (playerName == null) {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entryUuid);
                        playerName = offlinePlayer.getName();
                    }
                    if (playerName == null) playerName = "Unknown";
                    
                    String formattedBalance = plugin.getConfigManager().formatAmount(balance, currency);
                    sendCopyMessage(player, playerName, entryUuid.toString(), rank, formattedBalance);
                })
            );
        }

        boolean hasPrev = page > 1;
        boolean hasNext = topBalances.size() >= fetchLimit;

        this.addButton(38, new InventoryButton()
            .creator(p -> {
                if (!hasPrev) return createFillerPane();
                return createNavItem(p, XMaterial.ARROW, "<yellow>Previous Page", "<gray>Go to page " + (page - 1));
            })
            .consumer(event -> {
                if (hasPrev) {
                    plugin.getGuiManager().openGUI(new TopBalancesGUI(plugin, currency, page - 1), player);
                }
            })
        );

        this.addButton(40, new InventoryButton()
            .creator(p -> createNavItem(p, XMaterial.BARRIER, "<red>Close", "<gray>Close this menu"))
            .consumer(event -> player.closeInventory())
        );

        this.addButton(42, new InventoryButton()
            .creator(p -> {
                if (!hasNext) return createFillerPane();
                return createNavItem(p, XMaterial.ARROW, "<yellow>Next Page", "<gray>Go to page " + (page + 1));
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
        player.sendMessage(ColorUtil.colorizeToLegacy(player, "<gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(ColorUtil.colorizeToLegacy(player, "<gold><bold>#" + rank + " " + playerName));
        player.sendMessage(ColorUtil.colorizeToLegacy(player, "<gray>Balance: <white>" + balance));

        TextComponent nameComponent = new TextComponent(ColorUtil.colorizeToLegacy(player, "<yellow>► Click to copy IGN: <white>" + playerName));
        nameComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, playerName));
        nameComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(ColorUtil.colorizeToLegacy(player, "<gray>Click to copy <white>" + playerName + " <gray>to chat")).create()));
        player.spigot().sendMessage(nameComponent);

        TextComponent uuidComponent = new TextComponent(ColorUtil.colorizeToLegacy(player, "<yellow>► Click to copy UUID: <white>" + uuid));
        uuidComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid));
        uuidComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(ColorUtil.colorizeToLegacy(player, "<gray>Click to copy UUID to chat")).create()));
        player.spigot().sendMessage(uuidComponent);

        player.sendMessage(ColorUtil.colorizeToLegacy(player, "<gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    private ItemStack createPlayerHead(Player player, UUID uuid, String playerName, int rank, String balance) {
        ItemStack head = XMaterial.PLAYER_HEAD.parseItem();
        if (head == null) {
            head = new ItemStack(org.bukkit.Material.STONE);
        }

        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorizeToLegacy(player, "<gold>#" + rank + " <white>" + playerName));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.colorizeToLegacy(player, "<gray>Balance: <yellow>" + balance));
            lore.add(ColorUtil.colorizeToLegacy(player, "<gray>UUID: <white>" + uuid));
            lore.add("");
            lore.add(ColorUtil.colorizeToLegacy(player, "<yellow>Left-Click <gray>to copy IGN"));
            lore.add(ColorUtil.colorizeToLegacy(player, "<yellow>Right-Click <gray>to copy UUID"));
            meta.setLore(lore);

            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createLoadingItem() {
        ItemStack item = XMaterial.matchXMaterial("YELLOW_STAINED_GLASS_PANE").map(XMaterial::parseItem).orElse(XMaterial.STONE.parseItem());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtil.colorizeToLegacy("<yellow>Loading data..."));
        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.colorizeToLegacy("<gray>Please wait while we fetch"));
        lore.add(ColorUtil.colorizeToLegacy("<gray>the leaderboard data."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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

    private ItemStack createNavItem(Player player, XMaterial material, String name, String loreText) {
        ItemStack item = material.parseItem();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtil.colorizeToLegacy(player, name));
        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.colorizeToLegacy(player, loreText));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
