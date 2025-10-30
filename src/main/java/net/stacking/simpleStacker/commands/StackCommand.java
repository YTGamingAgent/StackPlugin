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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
            // Stack items FIRST before applying metadata
            for (int i = 0; i <= 35; i++) {
                ItemStack item = inventory.getItem(i);

                if (item == null || item.getType() == Material.AIR) continue;

                int targetMax = getTargetMaxStack(item, targets);

                // If this stack is already full, skip it
                if (item.getAmount() >= targetMax) continue;

                // Look for similar items in later slots to combine
                for (int j = i + 1; j <= 35; j++) {
                    ItemStack otherItem = inventory.getItem(j);

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
                        inventory.setItem(j, null);
                    }

                    stackedGroups++;
                }
            }

            // AFTER stacking, apply max stack size (without marker)
            for (int i = 0; i <= 35; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    applyMaxStackSize(item, targets);
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
     * Apply max stack size metadata to an item
     * Does NOT add the stacked_item marker to avoid component bloat
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
            // Only apply if not already set to the target max
            if (!meta.hasMaxStackSize() || meta.getMaxStackSize() != targetMax) {
                meta.setMaxStackSize(targetMax);

                // DO NOT add the stacked_item marker
                // This keeps component count minimal (7 → 8 instead of 7 → 9)
                // The /unstack command can still work by checking if maxStackSize != vanilla

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
     */
    private boolean canStack(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;
        if (item1.getType() != item2.getType()) return false;

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        // If both have no meta, they can stack
        if (meta1 == null && meta2 == null) return true;

        // If only one has meta, they can't stack
        if (meta1 == null || meta2 == null) return false;

        // Check durability - must be EXACTLY the same
        if (!durabilityMatches(item1, item2)) return false;

        // Check display name
        if (!Objects.equals(meta1.getDisplayName(), meta2.getDisplayName())) return false;

        // Check lore
        if (!Objects.equals(meta1.getLore(), meta2.getLore())) return false;

        // Check enchantments
        if (!meta1.getEnchants().equals(meta2.getEnchants())) return false;

        // Check custom model data
        if (meta1.hasCustomModelData() != meta2.hasCustomModelData()) return false;
        if (meta1.hasCustomModelData() && meta1.getCustomModelData() != meta2.getCustomModelData()) {
            return false;
        }

        // Check item flags
        if (!meta1.getItemFlags().equals(meta2.getItemFlags())) return false;

        // Check if unbreakable status matches
        if (meta1.isUnbreakable() != meta2.isUnbreakable()) return false;

        // Additional check for shulker boxes
        if (isShulkerBox(item1)) {
            return shulkerContentsMatch(item1, item2);
        }

        // Additional check for bundles
        if (isBundle(item1)) {
            return bundleContentsMatch(item1, item2);
        }

        // Compare persistent data containers (ignoring our marker)
        if (!persistentDataMatches(meta1, meta2)) return false;

        // Items can stack if all visible properties match
        return true;
    }

    /**
     * Check if persistent data containers match (ignoring our stacked_item marker)
     */
    private boolean persistentDataMatches(ItemMeta meta1, ItemMeta meta2) {
        PersistentDataContainer pdc1 = meta1.getPersistentDataContainer();
        PersistentDataContainer pdc2 = meta2.getPersistentDataContainer();

        // Get all keys from both containers
        var keys1 = pdc1.getKeys();
        var keys2 = pdc2.getKeys();

        // Remove our own marker from comparison
        keys1.remove(stackedKey);
        keys2.remove(stackedKey);

        // If different number of keys (excluding our marker), they don't match
        if (keys1.size() != keys2.size()) return false;

        // Check if all keys and values match
        for (NamespacedKey key : keys1) {
            if (!keys2.contains(key)) return false;

            // Try to compare the values
            try {
                Object val1 = pdc1.get(key, PersistentDataType.STRING);
                Object val2 = pdc2.get(key, PersistentDataType.STRING);
                if (!Objects.equals(val1, val2)) {
                    val1 = pdc1.get(key, PersistentDataType.INTEGER);
                    val2 = pdc2.get(key, PersistentDataType.INTEGER);
                    if (!Objects.equals(val1, val2)) {
                        val1 = pdc1.get(key, PersistentDataType.BYTE);
                        val2 = pdc2.get(key, PersistentDataType.BYTE);
                        if (!Objects.equals(val1, val2)) {
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }

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

    /**
     * Check if an item is a shulker box
     */
    private boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().contains("SHULKER_BOX");
    }

    /**
     * Check if an item is a bundle
     */
    private boolean isBundle(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        // Check for both BUNDLE and any colored variants (WHITE_BUNDLE, RED_BUNDLE, etc.)
        return typeName.equals("BUNDLE") || typeName.endsWith("_BUNDLE");
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

                if (c1.getType() != c2.getType()) return false;
                if (c1.getAmount() != c2.getAmount()) return false;
                if (!c1.isSimilar(c2)) return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error comparing shulker contents: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if two bundles have identical contents
     */
    private boolean bundleContentsMatch(ItemStack bundle1, ItemStack bundle2) {
        try {
            if (!(bundle1.getItemMeta() instanceof BundleMeta)) return true;
            if (!(bundle2.getItemMeta() instanceof BundleMeta)) return true;

            BundleMeta meta1 = (BundleMeta) bundle1.getItemMeta();
            BundleMeta meta2 = (BundleMeta) bundle2.getItemMeta();

            List<ItemStack> contents1 = meta1.getItems();
            List<ItemStack> contents2 = meta2.getItems();

            // Check if both are empty
            if (contents1.isEmpty() && contents2.isEmpty()) return true;

            // Check if sizes match
            if (contents1.size() != contents2.size()) return false;

            // Check each item matches exactly
            for (int i = 0; i < contents1.size(); i++) {
                ItemStack item1 = contents1.get(i);
                ItemStack item2 = contents2.get(i);

                if (item1 == null && item2 == null) continue;
                if (item1 == null || item2 == null) return false;

                if (item1.getType() != item2.getType()) return false;
                if (item1.getAmount() != item2.getAmount()) return false;
                if (!item1.isSimilar(item2)) return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error comparing bundle contents: " + e.getMessage());
            return false;
        }
    }
}
