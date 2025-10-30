package net.stacking.simpleStacker.handlers;

import net.stacking.simpleStacker.SimpleStacker;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class LanguageManager {

    private final SimpleStacker plugin;
    private FileConfiguration langConfig;
    private String defaultMessage;

    public LanguageManager(SimpleStacker plugin) {
        this.plugin = plugin;
        loadLanguage();
    }

    /**
     * Load or reload the language file
     */
    public void loadLanguage() {
        File langFile = new File(plugin.getDataFolder(), "language.yml");

        if (!langFile.exists()) {
            plugin.saveResource("language.yml", false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Load the default message for missing keys
        defaultMessage = langConfig.getString("default-missing-key-message",
                ChatColor.RED + "An error occurred. Please contact an administrator.");

        plugin.getLogger().info("Language file loaded successfully!");
    }

    /**
     * Get a message from the language file
     * The message key (e.g., "command.stack-success"), optional arguments to replace placeholders like {0}, {1}, etc.
     * return The formatted message with color codes
     */
    public String getMessage(String key, Object... args) {
        String message = langConfig.getString(key);

        // If key doesn't exist, return the configurable default message
        if (message == null) {
            plugin.getLogger().warning("Missing language key: " + key);
            return ChatColor.translateAlternateColorCodes('&', defaultMessage);
        }

        // Replace placeholders {0}, {1}, {2}, etc. with provided arguments
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }

        // Translate color codes (&c, &a, etc.) to Minecraft color codes
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Reload the file
     */
    public void reload() {
        loadLanguage();
    }
}
