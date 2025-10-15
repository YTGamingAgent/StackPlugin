package net.stacking.simpleStacker.commands;

import net.stacking.simpleStacker.SimpleStacker;
import net.stacking.simpleStacker.handlers.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Command to reload plugin configuration without restarting
 */
public class ReloadCommand implements CommandExecutor {

    private final SimpleStacker plugin;

    public ReloadCommand(SimpleStacker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();

        if (!sender.hasPermission("simplestacker.reload")) {
            sender.sendMessage(lang.getMessage("command.no-permission"));
            return true;
        }

        try {
            // Reload config.yml
            plugin.reloadConfig();

            // Reload language.yml
            plugin.getLanguageManager().reload();

            // Reload stack sizes from config
            plugin.getItemHandler().loadStackSizes();

            sender.sendMessage(lang.getMessage("command.reload-success"));
            plugin.getLogger().info(sender.getName() + " reloaded the plugin configuration");

        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("command.reload-error"));
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}
