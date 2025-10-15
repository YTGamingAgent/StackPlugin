package net.stacking.simpleStacker.commands;

import net.stacking.simpleStacker.SimpleStacker;
import net.stacking.simpleStacker.handlers.ItemHandler;
import net.stacking.simpleStacker.handlers.LanguageManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

public class StackCommand implements CommandExecutor {

    private final SimpleStacker plugin;
    private final NamespacedKey stackedKey;

    public StackCommand(SimpleStacker plugin) {
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

        if (!player.hasPermission("simplestacker.use")) {
            player.sendMessage(lang.getMessage("command.no-permission"));
            return true;
        }

        int stackedCount = stackInventorySafe(player);

        if (stackedCount > 0) {
            player.sendMessage(lang.getMessage("command.stack-success", stackedCount));
        } else {
            player.sendMessage(lang.getMessage("command.stack-none"));
        }

        return true;
    }

    /**
     * Safe stacking method for ensuring inventory doesn't clear
     * Excludes armor equipped and offhand stuff
     */
    private int stackInventorySafe(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemHandler handler = plugin.getItemHandler();
        Map<Material, Integer> targets = handler.getTargets();
        int stackedGroups = 0;

        try {
            // First pass: Apply max stack size metadata only to items in main inventory (0-35)
            for (int i = 0; i <= 35; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    applyMaxStackSize(item, targets);
                }
            }

            // Second pass: Stack similar items together (ONLY main inventory)
            for (int i = 0; i <= 35; i++) {
                ItemStack item = inventory.getItem(i);

                if (item == null || item.getType() == Material.AIR) continue;

                int targetMax = getTargetMaxStack(item, targets);

                // If this stack is already full, skip it
                if (item.getAmount() >= targetMax) continue;

                // Look for similar items in later slots to combine (ONLY in main inventory)
                for (int j = i + 1; j <= 35; j++) {
                    ItemStack otherItem = inventory.getItem(j);

                    if (otherItem == null || otherItem.getType() == Material.AIR) continue;

                    // Check if items can stack (includes durability check!)
                    if (!canStack(item, otherItem)) continue;

                    // Calculate how much we can transfer
                    int spaceLeft = targetMax - item.getAmount();
                    if (spaceLeft <= 0) break; // Current stack is 64

                    int transferAmount = Math.min(spaceLeft, otherItem.getAmount());

                    // Transfer items
                    item.setAmount(item.getAmount() + transferAmount);
                    otherItem.setAmount(otherItem.getAmount() - transferAmount);

                    // If the other stack is now empty, remove it
                    if (otherItem.getAmount() <= 0) {
                        inventory.setItem(j, null);
                    }

                    stackedGroups++;
                }
            }

            player.updateInventory();

        } catch (Exception e) {
            plugin.getLogger().severe("Error during safe stacking for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(plugin.getLanguageManager().getMessage("command.stack-error"));
            return 0;
        }

        return stackedGroups;
    }

    /**
     * Apply max stack size metadata to an item (now includes damageable items)
     * Marks items with persistent data so they can be identified and cleaned up
     */
    private void applyMaxStackSize(ItemStack item, Map<Material, Integer> targets) {
        if (item == null || item.getType() == Material.AIR) return;

        Integer targetMax = targets.get(item.getType());
        if (targetMax == null) return;

        // Validate target
        if (targetMax < 1 || targetMax > 99) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        try {
            // Apply max stack size (now works for damageable items too!)
            if (!meta.hasMaxStackSize() || meta.getMaxStackSize() != targetMax) {
                meta.setMaxStackSize(targetMax);

                // Mark this item as stacked by our plugin using PersistentDataContainer
                meta.getPersistentDataContainer().set(stackedKey, PersistentDataType.BYTE, (byte) 1);

                item.setItemMeta(meta);
            }
        } catch (Exception e) {
            // Silently fail for items that don't support max stack size
        }
    }

    /**
     * Get the target max stack size for an item
     */
    private int getTargetMaxStack(ItemStack item, Map<Material, Integer> targets) {
        Integer target = targets.get(item.getType());
        if (target != null && target >= 1 && target <= 99) {
            return target;
        }
        return item.getMaxStackSize();
    }

    /**
     * Check if two items can be stacked together
     * Included durability checker this time for more simplicity
     */
    private boolean canStack(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;
        if (item1.getType() != item2.getType()) return false;
        if (!item1.isSimilar(item2)) return false;

        // NEW: Check durability - must be EXACTLY the same
        if (!durabilityMatches(item1, item2)) return false;

        // Additional check for shulker boxes
        if (isShulkerBox(item1)) {
            return shulkerContentsMatch(item1, item2);
        }

        return true;
    }

    /**
     * Check if two items have the exact same durability
     */
    private boolean durabilityMatches(ItemStack item1, ItemStack item2) {
        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        // If neither has durability, they match
        if (!(meta1 instanceof Damageable) && !(meta2 instanceof Damageable)) {
            return true;
        }

        // If only one has durability, they don't match
        if (!(meta1 instanceof Damageable) || !(meta2 instanceof Damageable)) {
            return false;
        }

        // Both have durability - check if damage values are EXACTLY the same
        Damageable dam1 = (Damageable) meta1;
        Damageable dam2 = (Damageable) meta2;

        return dam1.getDamage() == dam2.getDamage();
    }

    /**
     * Check if an item is a shulker box
     */
    private boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        // Changed to contains() to catch both "SHULKER_BOX" and "COLOR_SHULKER_BOX"
        return item.getType().name().contains("SHULKER_BOX");
    }

    /**
     * Check if two shulker boxes have identical contents
     */
    private boolean shulkerContentsMatch(ItemStack shulker1, ItemStack shulker2) {
        try {
            if (!(shulker1.getItemMeta() instanceof BlockStateMeta)) return true;
            if (!(shulker2.getItemMeta() instanceof BlockStateMeta)) return true;

            BlockStateMeta meta1 = (BlockStateMeta) shulker1.getItemMeta();
            BlockStateMeta meta2 = (BlockStateMeta) shulker2.getItemMeta();

            if (!(meta1.getBlockState() instanceof ShulkerBox)) return true;
            if (!(meta2.getBlockState() instanceof ShulkerBox)) return true;

            ShulkerBox box1 = (ShulkerBox) meta1.getBlockState();
            ShulkerBox box2 = (ShulkerBox) meta2.getBlockState();

            ItemStack[] contents1 = box1.getInventory().getContents();
            ItemStack[] contents2 = box2.getInventory().getContents();

            if (contents1.length != contents2.length) return false;

            for (int i = 0; i < contents1.length; i++) {
                ItemStack c1 = contents1[i];
                ItemStack c2 = contents2[i];

                if (c1 == null && c2 == null) continue;
                if (c1 == null || c2 == null) return false;
                if (!c1.equals(c2)) return false;
            }

            return true;
        } catch (Exception e) {
            // If we can't compare, assume they don't match to be safe
            return false;
        }
    }
}
