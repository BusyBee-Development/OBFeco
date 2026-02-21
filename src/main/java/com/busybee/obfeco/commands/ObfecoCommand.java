package com.busybee.obfeco.commands;

import com.busybee.obfeco.Obfeco;
import com.busybee.obfeco.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class ObfecoCommand implements CommandExecutor, TabCompleter {
    private final Obfeco plugin;
    
    public ObfecoCommand(Obfeco plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        SubCommandType subCommand = SubCommandType.fromString(args[0]);
        
        if (subCommand == null) {
            sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " <red>Unknown command. Use <white>/" + label + " help</white> for help."));
            return true;
        }
        
        if (!sender.hasPermission(subCommand.getPermission())) {
            sender.sendMessage(ColorUtil.colorize(plugin.getMessageManager().getPrefix() + " " + plugin.getMessageManager().getMessage("no-permission")));
            return true;
        }
        
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        subCommand.execute(plugin, sender, subArgs);
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (SubCommandType type : SubCommandType.values()) {
                if (sender.hasPermission(type.getPermission())) {
                    String name = type.name().toLowerCase();
                    if (name.startsWith(args[0].toLowerCase())) {
                        completions.add(name);
                    }
                    for (String cmdAlias : type.getAliases()) {
                        if (cmdAlias.toLowerCase().startsWith(args[0].toLowerCase())) {
                            completions.add(cmdAlias);
                        }
                    }
                }
            }
            return completions;
        }
        
        if (args.length > 1) {
            SubCommandType subCommand = SubCommandType.fromString(args[0]);
            if (subCommand != null && sender.hasPermission(subCommand.getPermission())) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.tabComplete(plugin, sender, subArgs);
            }
        }
        
        return Collections.emptyList();
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("<gradient:gold:yellow>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>"));
        sender.sendMessage(ColorUtil.colorize("<gold><bold>Obfeco Commands"));
        sender.sendMessage(ColorUtil.colorize("<gradient:gold:yellow>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>"));
        
        if (sender.hasPermission("obfeco.balance")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco balance <player> <currency> <gray>- Check balance"));
        }
        if (sender.hasPermission("obfeco.top")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco top <currency> <gray>- View leaderboards"));
        }
        if (sender.hasPermission("obfeco.give")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco give <player> <currency> <amount> [-s] <gray>- Give currency"));
        }
        if (sender.hasPermission("obfeco.take")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco take <player> <currency> <amount> [-s] <gray>- Take currency"));
        }
        if (sender.hasPermission("obfeco.set")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco set <player> <currency> <amount> <gray>- Set balance"));
        }
        if (sender.hasPermission("obfeco.create")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco manage <gray>- Open currency manager GUI"));
        }
        if (sender.hasPermission("obfeco.admin")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco scan <plugin> <gray>- Scan for currencies to migrate"));
        }
        if (sender.hasPermission("obfeco.reload")) {
            sender.sendMessage(ColorUtil.colorize("<gold>/obfeco reload <gray>- Reload configuration"));
        }
        
        sender.sendMessage(ColorUtil.colorize("<gradient:gold:yellow>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</gradient>"));
    }
}