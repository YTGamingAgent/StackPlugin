package net.stacking.simpleStacker.commands;

import net.stacking.simpleStacker.SimpleStacker;
import net.stacking.simpleStacker.handlers.LanguageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Command to unstack items and remove custom stack metadata
 * This ensures items return to their original vanilla state
 */
public class UnstackCommand implements CommandExecutor {

    private final SimpleStacker plugin;
    private final NamespacedKey stackedKey;

    public UnstackCommand(SimpleStacker plugin) {
        this.plugin = plugin;
        this.stackedKey = new NamespacedKey(plugin, "stacked_item");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();

        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getMessage("command.players-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("simplestacker.unstack")) {
            player.sendMessage(lang.getMessage("command.no-permission"));
            return true;
        }

        int unstackedCount = unstackInventory(player);

        if (unstackedCount > 0) {
            player.sendMessage(lang.getMessage("command.unstack-success", unstackedCount));
        } else {
            player.sendMessage(lang.getMessage("command.unstack-none"));
        }

        return true;
    }

    /**
     * Remove custom stack metadata from all items in player's inventory
     * This makes items return to their vanilla max stack size of 64
     */
    private int unstackInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        int unstackedCount = 0;

        try {
            // Check all inventory slots (0-40: main inventory + armor + offhand)
            for (int i = 0; i <= 40; i++) {
                ItemStack item = inventory.getItem(i);

                if (item == null || item.getType() == Material.AIR) continue;

                ItemMeta meta = item.getItemMeta();
                if (meta == null) continue;

                // Check if this item was stacked by our plugin
                if (meta.getPersistentDataContainer().has(stackedKey, PersistentDataType.BYTE)) {
                    // Remove the custom max stack size
                    if (meta.hasMaxStackSize()) {
                        meta.setMaxStackSize(null); // Reset to default
                    }

                    // Remove the marker
                    meta.getPersistentDataContainer().remove(stackedKey);

                    item.setItemMeta(meta);
                    unstackedCount++;
                }
            }

            player.updateInventory();

        } catch (Exception e) {
            plugin.getLogger().severe("Error during unstacking for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(plugin.getLanguageManager().getMessage("command.unstack-error"));
            return 0;
        }

        return unstackedCount;
    }
}
