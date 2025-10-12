package net.stacking.simpleStacker;

import net.stacking.simpleStacker.commands.StackCommand;
import net.stacking.simpleStacker.handlers.ItemHandler;
import net.stacking.simpleStacker.listeners.DurabilityUsageListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SimpleStacker extends JavaPlugin {

    private static SimpleStacker instance;
    private ItemHandler itemHandler;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Load material stack sizes from config
        itemHandler = new ItemHandler();
        itemHandler.loadStackSizes();

        // REMOVED: StackingListener (no auto-stacking!)
        // Items will only stack when /stack command is used

        // Register listener to prevent using stacked damageable items
        getServer().getPluginManager().registerEvents(
                new DurabilityUsageListener(),
                this
        );

        // Register command executor from plugin.yml
        Objects.requireNonNull(getCommand("stack"), "Command 'stack' not found in plugin.yml")
                .setExecutor(new StackCommand(this));

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
}
