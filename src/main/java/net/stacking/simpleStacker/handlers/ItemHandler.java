package net.stacking.simpleStacker.handlers;

import net.stacking.simpleStacker.SimpleStacker;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ItemHandler {

    private final Map<Material, Integer> targets = new HashMap<>();

    public void loadStackSizes() {
        targets.clear(); // Clear existing entries before reloading

        Logger log = SimpleStacker.getInstance().getLogger();
        ConfigurationSection itemsSection = SimpleStacker.getInstance().getConfig().getConfigurationSection("items");

        if (itemsSection == null) {
            log.warning("No items section found in config!");
            return;
        }

        int success = 0, invalid = 0;

        for (String materialName : itemsSection.getKeys(false)) {
            int stackSize = itemsSection.getInt(materialName);
            Material material = Material.matchMaterial(materialName);

            if (material == null) {
                log.fine("Unknown material (skipping): " + materialName);
                invalid++;
                continue;
            }

            if (stackSize < 1 || stackSize > 99) {
                log.fine("Invalid stack size " + stackSize + " for " + material + " (must be 1..99) - skipping");
                invalid++;
                continue;
            }

            // Removed durability check - now damageable items can stack
            targets.put(material, stackSize);
            success++;
        }

        log.info("Loaded " + success + " stack rules, " + invalid + " invalid.");
    }

    public Map<Material, Integer> getTargets() {
        return targets;
    }
}
