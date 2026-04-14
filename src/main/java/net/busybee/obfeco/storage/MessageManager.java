package net.busybee.obfeco.storage;

import net.busybee.obfeco.Obfeco;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageManager {
    private final Obfeco plugin;
    private FileConfiguration messages;
    
    public MessageManager(Obfeco plugin) {
        this.plugin = plugin;
        loadMessages();
    }
    
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }
    
    public void reload() {
        loadMessages();
    }
    public String getMessage(String path) {
        return messages.getString(path, "&cMessage not found: " + path);
    }
    public String getPrefix() {
        return messages.getString("prefix", "<gold>[Obfeco]");
    }
}
