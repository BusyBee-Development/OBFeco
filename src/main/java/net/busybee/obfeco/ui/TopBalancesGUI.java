package net.busybee.obfeco.ui;

import fr.mrmicky.fastinv.FastInv;
import net.busybee.obfeco.Obfeco;
import net.busybee.obfeco.core.Currency;
import net.busybee.obfeco.database.DatabaseManager;
import net.busybee.obfeco.util.ColorUtil;
import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import net.busybee.obfeco.util.FoliaUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public final class TopBalancesGUI extends FastInv {

    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int PAGE_SIZE = ENTRY_SLOTS.length;

    private final Obfeco plugin;
    private final Currency currency;
    private final int page;

    private List<DatabaseManager.LeaderboardEntry> leaderboardEntries;
    private boolean loading;

    public TopBalancesGUI(Obfeco plugin, Currency currency, int page) {
        super(
                45,
                ColorUtil.colorizeToLegacy(
                        "<gold><bold>Top " + currency.getDisplayName() + " <gray>» Page " + page
                )
        );

        this.plugin = plugin;
        this.currency = currency;
        this.page = Math.max(1, page);
    }

    @Override
    public void open(Player player) {
        renderLoadingState(player);
        super.open(player);
        loadLeaderboard(player);
    }

    private void loadLeaderboard(Player player) {
        if (loading) {
            return;
        }

        loading = true;

        int offset = (page - 1) * PAGE_SIZE;
        int fetchLimit = offset + PAGE_SIZE;

        plugin.getDatabaseManager()
                .getTopBalancesExtendedAsync(currency.getId(), fetchLimit)
                .thenAccept(entries -> FoliaUtil.run(plugin, () -> {
                    leaderboardEntries = entries;
                    loading = false;

                    if (player.getOpenInventory().getTopInventory().equals(getInventory())) {
                        renderLeaderboard(player);
                    }
                }));
    }

    private void renderLoadingState(Player player) {
        clearItems();

        for (int slot : ENTRY_SLOTS) {
            setItem(slot, createLoadingItem());
        }

        setStaticButtons(player, false, false);
    }

    private void renderLeaderboard(Player player) {
        clearItems();

        int offset = (page - 1) * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE; i++) {
            int dataIndex = offset + i;
            int slot = ENTRY_SLOTS[i];
            int rank = dataIndex + 1;

            if (leaderboardEntries == null || dataIndex >= leaderboardEntries.size()) {
                setItem(slot, createEmptyItem());
                continue;
            }

            DatabaseManager.LeaderboardEntry entry = leaderboardEntries.get(dataIndex);

            UUID uuid = entry.getUuid();
            double balance = entry.getBalance();

            String playerName = entry.getName();
            if (playerName == null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                playerName = offlinePlayer.getName();
            }

            if (playerName == null) {
                playerName = "Unknown";
            }

            String formattedBalance = plugin.getConfigManager().formatAmount(balance, currency);

            String finalPlayerName = playerName;

            setItem(slot, createPlayerItem(
                    player,
                    uuid,
                    playerName,
                    rank,
                    formattedBalance
            ), event -> sendPlayerInfo(player, finalPlayerName, uuid, rank, formattedBalance));
        }

        boolean hasPreviousPage = page > 1;
        boolean hasNextPage = leaderboardEntries != null && leaderboardEntries.size() >= offset + PAGE_SIZE;

        setStaticButtons(player, hasPreviousPage, hasNextPage);
    }

    private void setStaticButtons(Player player, boolean hasPreviousPage, boolean hasNextPage) {
        setItem(38,
                hasPreviousPage
                        ? createNavigationItem(player, XMaterial.ARROW, "<yellow>Previous Page", "<gray>Go to page " + (page - 1))
                        : createPane(),
                event -> {
                    if (hasPreviousPage) {
                        new TopBalancesGUI(plugin, currency, page - 1).open(player);
                    }
                });

        setItem(40,
                createNavigationItem(player, XMaterial.BARRIER, "<red>Close", "<gray>Close this menu"),
                event -> player.closeInventory()
        );

        setItem(42,
                hasNextPage
                        ? createNavigationItem(player, XMaterial.ARROW, "<yellow>Next Page", "<gray>Go to page " + (page + 1))
                        : createPane(),
                event -> {
                    if (hasNextPage) {
                        new TopBalancesGUI(plugin, currency, page + 1).open(player);
                    }
                });
    }

    private void sendPlayerInfo(
            Player viewer,
            String playerName,
            UUID uuid,
            int rank,
            String formattedBalance
    ) {
        viewer.sendMessage(ColorUtil.colorizeToLegacy(viewer, "<gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        viewer.sendMessage(ColorUtil.colorizeToLegacy(viewer, "<gold><bold>#" + rank + " <white>" + playerName));
        viewer.sendMessage(ColorUtil.colorizeToLegacy(viewer, "<gray>Balance: <white>" + formattedBalance));

        TextComponent ignComponent = new TextComponent(
                ColorUtil.colorizeToLegacy(viewer,
                        "<yellow>► Click to copy IGN: <white>" + playerName)
        );

        ignComponent.setClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                playerName
        ));

        ignComponent.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(
                        ColorUtil.colorizeToLegacy(viewer,
                                "<gray>Click to copy <white>" + playerName)
                ).create()
        ));

        viewer.spigot().sendMessage(ignComponent);

        TextComponent uuidComponent = new TextComponent(
                ColorUtil.colorizeToLegacy(viewer,
                        "<yellow>► Click to copy UUID: <white>" + uuid)
        );

        uuidComponent.setClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                uuid.toString()
        ));

        uuidComponent.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(
                        ColorUtil.colorizeToLegacy(viewer,
                                "<gray>Click to copy UUID")
                ).create()
        ));

        viewer.spigot().sendMessage(uuidComponent);
        viewer.sendMessage(ColorUtil.colorizeToLegacy(viewer, "<gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    private ItemStack createPlayerItem(
            Player viewer,
            UUID uuid,
            String playerName,
            int rank,
            String balance
    ) {
        ItemStack item = XMaterial.PLAYER_HEAD.parseItem();
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.setDisplayName(ColorUtil.colorizeToLegacy(
                viewer,
                "<gold>#" + rank + " <white>" + playerName
        ));

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.colorizeToLegacy(viewer, "<gray>Balance: <yellow>" + balance));
        lore.add(ColorUtil.colorizeToLegacy(viewer, "<gray>UUID: <white>" + uuid));
        lore.add("");
        lore.add(ColorUtil.colorizeToLegacy(viewer, "<yellow>Click <gray>to copy details"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLoadingItem() {
        ItemStack item = XMaterial.YELLOW_STAINED_GLASS_PANE.parseItem();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorUtil.colorizeToLegacy("<yellow>Loading..."));
        meta.setLore(List.of(
                ColorUtil.colorizeToLegacy("<gray>Fetching leaderboard data")
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyItem() {
        ItemStack item = XMaterial.GRAY_STAINED_GLASS_PANE.parseItem();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(" ");
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createPane() {
        ItemStack item = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(" ");
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createNavigationItem(
            Player player,
            XMaterial material,
            String name,
            String loreLine
    ) {
        ItemStack item = material.parseItem();
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorUtil.colorizeToLegacy(player, name));
        meta.setLore(List.of(ColorUtil.colorizeToLegacy(player, loreLine)));

        item.setItemMeta(meta);
        return item;
    }
}
