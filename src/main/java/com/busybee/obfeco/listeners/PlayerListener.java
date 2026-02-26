package com.busybee.obfeco.listeners;

import com.busybee.obfeco.Obfeco;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public class PlayerListener implements Listener {
    private final Obfeco plugin;
    
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getDatabaseManager().updatePlayerName(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        plugin.getCurrencyManager().loadPlayerData(event.getPlayer().getUniqueId());
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfigManager().isAutoSaveOnQuit()) {
            plugin.getCurrencyManager().savePlayerData(event.getPlayer().getUniqueId());
        }
        plugin.getCurrencyManager().unloadPlayerData(event.getPlayer().getUniqueId());
    }
}