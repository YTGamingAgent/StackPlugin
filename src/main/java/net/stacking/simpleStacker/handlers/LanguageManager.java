package net.stacking.simpleStacker.handlers;

import net.stacking.simpleStacker.SimpleStacker;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Manages language messages from language.yml
 */
public class LanguageManager {

    private final SimpleStacker plugin;
    private File languageFile;
    private FileConfiguration languageConfig;

    public LanguageManager(SimpleStacker plugin) {
        this.plugin = plugin;
        loadLanguageFile();
    }

    /**
     * Load or create language.yml
     */
    private void loadLanguageFile() {
        languageFile = new File(plugin.getDataFolder(), "language.yml");

        if (!languageFile.exists()) {
            plugin.saveResource("language.yml", false);
        }

        languageConfig = YamlConfiguration.loadConfiguration(languageFile);

        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("language.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            languageConfig.setDefaults(defaultConfig);
        }
    }

    /**
     * Reload language.yml from disk
     */
    public void reload() {
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        plugin.getLogger().info("Language configuration reloaded");
    }

    /**
     * Get a message from language.yml with color codes translated
     */
    public String getMessage(String path) {
        String message = languageConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Missing language key: " + path);
            return ChatColor.RED + "Missing message: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Get a message with a placeholder replacement
     */
    public String getMessage(String path, Object... replacements) {
        String message = getMessage(path);

        for (int i = 0; i < replacements.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(replacements[i]));
        }

        return message;
    }

    /**
     * Save language.yml
     */
    public void save() {
        try {
            languageConfig.save(languageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save language.yml: " + e.getMessage());
        }
    }
}
