package net.stacking.simpleStacker.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

/**
 * Special handler for milk buckets in stacks
 * Allows drinking milk from a stack, returns empty bucket to first available slot
 */
public class MilkBucketListener implements Listener {

    private final Plugin plugin;

    public MilkBucketListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle milk bucket consumption from stacks
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMilkBucketConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();

        // Only handle milk buckets
        if (item == null || item.getType() != Material.MILK_BUCKET) {
            return;
        }

        // Only handle stacked milk buckets (amount > 1)
        if (item.getAmount() <= 1) {
            return; // Let vanilla handle single milk buckets
        }

        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();

        // Cancel the default behavior
        event.setCancelled(true);

        // Apply milk bucket effects (clear all potion effects)
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType())
        );

        // Schedule the item manipulation for next tick to avoid conflicts
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack heldItem = inv.getItemInMainHand();

                // Verify it's still a milk bucket stack
                if (heldItem.getType() != Material.MILK_BUCKET || heldItem.getAmount() <= 0) {
                    return;
                }

                // Reduce milk bucket count by 1
                int newAmount = heldItem.getAmount() - 1;

                if (newAmount > 0) {
                    heldItem.setAmount(newAmount);
                } else {
                    inv.setItemInMainHand(new ItemStack(Material.AIR));
                }

                // Create empty bucket to return
                ItemStack emptyBucket = new ItemStack(Material.BUCKET, 1);

                // Try to add to hotbar first (slots 0-8)
                boolean added = false;
                for (int i = 0; i < 9; i++) {
                    ItemStack slotItem = inv.getItem(i);

                    // Empty slot found in hotbar
                    if (slotItem == null || slotItem.getType() == Material.AIR) {
                        inv.setItem(i, emptyBucket);
                        added = true;
                        break;
                    }

                    // Stack with existing buckets in hotbar
                    if (slotItem.getType() == Material.BUCKET && slotItem.getAmount() < slotItem.getMaxStackSize()) {
                        slotItem.setAmount(slotItem.getAmount() + 1);
                        added = true;
                        break;
                    }
                }

                // If hotbar is full, try main inventory (slots 9-35)
                if (!added) {
                    for (int i = 9; i < 36; i++) {
                        ItemStack slotItem = inv.getItem(i);

                        // Empty slot found in inventory
                        if (slotItem == null || slotItem.getType() == Material.AIR) {
                            inv.setItem(i, emptyBucket);
                            added = true;
                            break;
                        }

                        // Stack with existing buckets in inventory
                        if (slotItem.getType() == Material.BUCKET && slotItem.getAmount() < slotItem.getMaxStackSize()) {
                            slotItem.setAmount(slotItem.getAmount() + 1);
                            added = true;
                            break;
                        }
                    }
                }

                // If inventory is completely full, drop the bucket
                if (!added) {
                    player.getWorld().dropItemNaturally(player.getLocation(), emptyBucket);
                }

                // Update inventory
                player.updateInventory();
            }
        }.runTaskLater(plugin, 1L);
    }
}
