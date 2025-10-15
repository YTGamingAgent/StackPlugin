package net.stacking.simpleStacker;

import net.stacking.simpleStacker.commands.ReloadCommand;
import net.stacking.simpleStacker.commands.StackCommand;
import net.stacking.simpleStacker.commands.StackShulkerInteriorCommand;
import net.stacking.simpleStacker.commands.UnstackCommand;
import net.stacking.simpleStacker.handlers.ItemHandler;
import net.stacking.simpleStacker.handlers.LanguageManager;
import net.stacking.simpleStacker.listeners.DurabilityUsageListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SimpleStacker extends JavaPlugin {

    private static SimpleStacker instance;
    private ItemHandler itemHandler;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config files
        saveDefaultConfig();
        saveResource("language.yml", false);

        // Initialize language manager
        languageManager = new LanguageManager(this);

        // Load material stack sizes from config
        itemHandler = new ItemHandler();
        itemHandler.loadStackSizes();

        // Removed: StackingListener to prevent automation
        // Items will only stack when /stack command is used

        // Register listener to prevent using stacked damageable items
        getServer().getPluginManager().registerEvents(
                new DurabilityUsageListener(),
                this
        );

        // Register command executors from plugin.yml
        Objects.requireNonNull(getCommand("stack"), "Command 'stack' not found in plugin.yml")
                .setExecutor(new StackCommand(this));

        Objects.requireNonNull(getCommand("unstack"), "Command 'unstack' not found in plugin.yml")
                .setExecutor(new UnstackCommand(this));

        Objects.requireNonNull(getCommand("stackreload"), "Command 'stackreload' not found in plugin.yml")
                .setExecutor(new ReloadCommand(this));

        Objects.requireNonNull(getCommand("stackshulkerinterior"), "Command 'stackshulkerinterior' not found in plugin.yml")
                .setExecutor(new StackShulkerInteriorCommand(this));

        getLogger().info("SimpleStacker enabled - Manual stacking only mode!");
        getLogger().info("Stack rules loaded: " + itemHandler.getTargets().size());
        getLogger().info("Use /stack command to stack items (no auto-stacking)");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleStacker has been disabled!");
    }

    public static SimpleStacker getInstance() {
        return instance;
    }

    public ItemHandler getItemHandler() {
        return itemHandler;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}
