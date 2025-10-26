package net.stacking.simpleStacker.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;

/**
 * Prevents players from using stacked damageable items, for better simplicity
 * Also prevents using stacked consumables like buckets, soup, potions, etc
 * Also prevents using stacked shulker boxes and bundles
 * Error message: "You must take one item out of the stack and use that!"
 */
public class DurabilityUsageListener implements Listener {

    private static final String ERROR_MESSAGE = ChatColor.RED + "You must take one item out of the stack and use that!";

    /**
     * Check if an item is damageable
     */
    private boolean isDamageable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().getMaxDurability() > 0;
    }

    /**
     * Check if an item is a shulker box
     */
    private boolean isShulkerBox(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String typeName = item.getType().name();
        return typeName.contains("SHULKER_BOX");
    }

    /**
     * Check if an item is a bundle
     */
    private boolean isBundle(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String typeName = item.getType().name();
        // Check for both BUNDLE and any colored variants (WHITE_BUNDLE, RED_BUNDLE, etc.)
        return typeName.equals("BUNDLE") || typeName.endsWith("_BUNDLE");
    }

    /**
     * Check if an item is a consumable/useable item that shouldn't be stacked when used
     * EXCLUDES milk buckets (handled by MilkBucketListener)
     */
    private boolean isConsumable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material type = item.getType();
        String typeName = type.name();

        // Buckets (excluding regular bucket, water bucket, AND milk bucket)
        if (typeName.contains("BUCKET") &&
                type != Material.BUCKET &&
                type != Material.WATER_BUCKET &&
                type != Material.MILK_BUCKET) { // EXCLUDE MILK BUCKET - handled by MilkBucketListener
            return true; // Lava bucket, powder snow bucket, fish buckets, etc.
        }

        // Food items (soups, stews, etc.)
        if (typeName.contains("SOUP") || typeName.contains("STEW")) {
            return true;
        }

        // Potions (all types)
        if (typeName.contains("POTION") || type == Material.GLASS_BOTTLE) {
            return true;
        }

        // Other consumables
        switch (type) {
            // All stews
            case MUSHROOM_STEW:
            case RABBIT_STEW:
            case BEETROOT_SOUP:
            case SUSPICIOUS_STEW:

                // All bucket variants EXCEPT MILK_BUCKET (handled separately)
            case POWDER_SNOW_BUCKET:
            case AXOLOTL_BUCKET:
            case TADPOLE_BUCKET:
            case PUFFERFISH_BUCKET:
            case SALMON_BUCKET:
            case COD_BUCKET:
            case TROPICAL_FISH_BUCKET:
            case LAVA_BUCKET:

                // Bottles
            case HONEY_BOTTLE:
            case EXPERIENCE_BOTTLE:
            case DRAGON_BREATH:

                // All potions (regular, splash, lingering)
            case POTION:
            case SPLASH_POTION:
            case LINGERING_POTION:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if a block is a container that can be opened
     */
    private boolean isContainer(Block block) {
        if (block == null) return false;

        Material type = block.getType();
        String typeName = type.name();

        // Check for shulker boxes
        if (typeName.contains("SHULKER_BOX")) {
            return true;
        }

        // Check for other containers
        switch (type) {
            case CHEST:
            case TRAPPED_CHEST:
            case ENDER_CHEST:
            case BARREL:
            case HOPPER:
            case DROPPER:
            case DISPENSER:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case BREWING_STAND:
            case CRAFTING_TABLE:
            case ENCHANTING_TABLE:
            case ANVIL:
            case CHIPPED_ANVIL:
            case DAMAGED_ANVIL:
            case GRINDSTONE:
            case CARTOGRAPHY_TABLE:
            case LOOM:
            case SMITHING_TABLE:
            case STONECUTTER:
            case BEACON:
            case LECTERN:
            case COMPOSTER:
            case JUKEBOX:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if item is stacked (amount > 1) and damageable
     */
    private boolean isStackedDamageable(ItemStack item) {
        return item != null && item.getAmount() > 1 && isDamageable(item);
    }

    /**
     * Check if item is stacked (amount > 1) and consumable
     */
    private boolean isStackedConsumable(ItemStack item) {
        return item != null && item.getAmount() > 1 && isConsumable(item);
    }

    /**
     * Check if item is stacked (amount > 1) and is a shulker box
     */
    private boolean isStackedShulkerBox(ItemStack item) {
        return item != null && item.getAmount() > 1 && isShulkerBox(item);
    }

    /**
     * Check if item is stacked (amount > 1) and is a bundle
     */
    private boolean isStackedBundle(ItemStack item) {
        return item != null && item.getAmount() > 1 && isBundle(item);
    }

    /**
     * Prevent using tools (pickaxe, axe, shovel, hoe, armors, etc.) when stacked
     * Also prevents using buckets and other consumables
     * Also prevents using stacked shulker boxes and bundles
     * Handles right-click and left-click actions
     * Allows opening containers even when holding stacked items!
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();

        // IMPORTANT: If player is right-clicking a container, allow it!
        if (action == Action.RIGHT_CLICK_BLOCK && isContainer(clickedBlock)) {
            return; // Don't prevent opening containers
        }

        // Prevent using stacked shulker boxes
        if (isStackedShulkerBox(item)) {
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                player.sendMessage(ERROR_MESSAGE);
                return;
            }
        }

        // Prevent using stacked bundles
        if (isStackedBundle(item)) {
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                player.sendMessage(ERROR_MESSAGE);
                return;
            }
        }

        // Check if player is trying to use a stacked damageable item
        if (isStackedDamageable(item)) {
            // Check if action would use the item
            boolean wouldUseItem = false;

            // Right-click actions that use durability
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                Material type = item.getType();
                // Tools that use durability on right-click
                if (type.name().endsWith("_HOE") || // Tilling
                        type.name().endsWith("_AXE") || // Stripping logs
                        type.name().endsWith("_SHOVEL") || // Path creation
                        type == Material.FLINT_AND_STEEL ||
                        type == Material.SHEARS ||
                        type == Material.FISHING_ROD || // Added fishing rod
                        type == Material.CARROT_ON_A_STICK ||
                        type == Material.WARPED_FUNGUS_ON_A_STICK) {
                    wouldUseItem = true;
                }
            }

            // Left-click actions (breaking blocks handled in BlockBreakEvent)
            // Prevented the swing animation here
            if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                if (item.getType().name().endsWith("_PICKAXE") ||
                        item.getType().name().endsWith("_AXE") ||
                        item.getType().name().endsWith("_SHOVEL") ||
                        item.getType().name().endsWith("_SWORD") ||
                        item.getType().name().endsWith("_HOE")) {
                    wouldUseItem = true;
                }
            }

            if (wouldUseItem) {
                event.setCancelled(true);
                player.sendMessage(ERROR_MESSAGE);
                return;
            }
        }

        // Check if player is trying to use a stacked consumable item
        if (isStackedConsumable(item)) {
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                player.sendMessage(ERROR_MESSAGE);
            }
        }
    }

    /**
     * Prevent breaking blocks with stacked tools
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isStackedDamageable(item)) {
            event.setCancelled(true);
            player.sendMessage(ERROR_MESSAGE);
        }
    }

    /**
     * Prevent attacking entities with stacked weapons/tools
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isStackedDamageable(item)) {
            event.setCancelled(true);
            player.sendMessage(ERROR_MESSAGE);
        }
    }

    /**
     * Catch-all: Prevent any item damage when stacked
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();

        if (item != null && item.getAmount() > 1 && isDamageable(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ERROR_MESSAGE);
        }
    }

    /**
     * Prevent consuming stacked food/potions (EXCLUDES milk buckets)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();

        // Allow MilkBucketListener to handle stacked milk buckets
        if (item != null && item.getType() == Material.MILK_BUCKET && item.getAmount() > 1) {
            return; // Don't cancel - let MilkBucketListener handle it
        }

        if (isStackedConsumable(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ERROR_MESSAGE);
        }
    }
}
