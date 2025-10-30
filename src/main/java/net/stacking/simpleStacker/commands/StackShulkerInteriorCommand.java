package net.stacking.simpleStacker.commands;

import net.stacking.simpleStacker.SimpleStacker;
import net.stacking.simpleStacker.handlers.ItemHandler;
import net.stacking.simpleStacker.handlers.LanguageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Command to stack items inside a shulker box held in main hand or offhand
 * Usage: /stackshulkerinterior or /ssi
 */
public class StackShulkerInteriorCommand implements CommandExecutor {

    private final SimpleStacker plugin;
    private final NamespacedKey stackedKey;

    public StackShulkerInteriorCommand(SimpleStacker plugin) {
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

        if (!player.hasPermission("simplestacker.stackshulker")) {
            player.sendMessage(lang.getMessage("command.no-permission"));
            return true;
        }

        // Check main hand first, then offhand
        ItemStack shulkerItem = player.getInventory().getItemInMainHand();
        if (!isShulkerBox(shulkerItem)) {
            shulkerItem = player.getInventory().getItemInOffHand();
            if (!isShulkerBox(shulkerItem)) {
                player.sendMessage(lang.getMessage("command.shulker-not-found"));
                return true;
            }
        }

        int stackedCount = stackShulkerContents(shulkerItem, player);

        if (stackedCount > 0) {
            player.sendMessage(lang.getMessage("command.shulker-stack-success", stackedCount));
        } else {
            player.sendMessage(lang.getMessage("command.shulker-stack-none"));
        }

        return true;
    }

    /**
     * Check if an item is a shulker box
     */
    private boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        // Changed to contains() to catch both "SHULKER_BOX" and "COLOR_SHULKER_BOX"
        return item.getType().name().contains("SHULKER_BOX");
    }

    /**
     * Stack items inside a shulker box
     */
    private int stackShulkerContents(ItemStack shulkerItem, Player player) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta)) return 0;

        BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();
        if (!(meta.getBlockState() instanceof ShulkerBox)) return 0;

        ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
        Inventory shulkerInv = shulkerBox.getInventory();

        ItemHandler handler = plugin.getItemHandler();
        Map<Material, Integer> targets = handler.getTargets();
        int stackedGroups = 0;

        try {
            // First pass: Apply max stack size metadata to items in shulker
            for (int i = 0; i < shulkerInv.getSize(); i++) {
                ItemStack item = shulkerInv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    applyMaxStackSize(item, targets);
                }
            }

            // Second pass: Stack similar items together
            for (int i = 0; i < shulkerInv.getSize(); i++) {
                ItemStack item = shulkerInv.getItem(i);

                if (item == null || item.getType() == Material.AIR) continue;

                int targetMax = getTargetMaxStack(item, targets);

                // If this stack is already full, skip it
                if (item.getAmount() >= targetMax) continue;

                // Look for similar items in later slots to combine
                for (int j = i + 1; j < shulkerInv.getSize(); j++) {
                    ItemStack otherItem = shulkerInv.getItem(j);

                    if (otherItem == null || otherItem.getType() == Material.AIR) continue;

                    // Check if items can stack
                    if (!canStack(item, otherItem)) continue;

                    // Calculate how much we can transfer
                    int spaceLeft = targetMax - item.getAmount();
                    if (spaceLeft <= 0) break;

                    int transferAmount = Math.min(spaceLeft, otherItem.getAmount());

                    // Transfer items
                    item.setAmount(item.getAmount() + transferAmount);
                    otherItem.setAmount(otherItem.getAmount() - transferAmount);

                    // If the other stack is now empty, remove it
                    if (otherItem.getAmount() <= 0) {
                        shulkerInv.setItem(j, null);
                    }

                    stackedGroups++;
                }
            }

            // Save the modified shulker box back to the item
            meta.setBlockState(shulkerBox);
            shulkerItem.setItemMeta(meta);

        } catch (Exception e) {
            plugin.getLogger().severe("Error stacking shulker contents for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(plugin.getLanguageManager().getMessage("command.shulker-stack-error"));
            return 0;
        }

        return stackedGroups;
    }

    /**
     * Apply max stack size metadata to an item
     */
    private void applyMaxStackSize(ItemStack item, Map<Material, Integer> targets) {
        if (item == null || item.getType() == Material.AIR) return;

        Integer targetMax = targets.get(item.getType());
        if (targetMax == null) return;

        if (targetMax < 1 || targetMax > 99) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        try {
            if (!meta.hasMaxStackSize() || meta.getMaxStackSize() != targetMax) {
                meta.setMaxStackSize(targetMax);
                meta.getPersistentDataContainer().set(stackedKey, PersistentDataType.BYTE, (byte) 1);
                item.setItemMeta(meta);
            }
        } catch (Exception e) {
            // Silently fail
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
     */
    private boolean canStack(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;
        if (item1.getType() != item2.getType()) return false;
        if (!item1.isSimilar(item2)) return false;

        if (!durabilityMatches(item1, item2)) return false;

        return true;
    }

    /**
     * Check if two items have the exact same durability
     */
    private boolean durabilityMatches(ItemStack item1, ItemStack item2) {
        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        if (!(meta1 instanceof Damageable) && !(meta2 instanceof Damageable)) {
            return true;
        }

        if (!(meta1 instanceof Damageable) || !(meta2 instanceof Damageable)) {
            return false;
        }

        Damageable dam1 = (Damageable) meta1;
        Damageable dam2 = (Damageable) meta2;

        return dam1.getDamage() == dam2.getDamage();
    }
}
