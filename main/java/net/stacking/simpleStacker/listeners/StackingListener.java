package net.stacking.simpleStacker.listeners;

import org.bukkit.event.Listener;

/**
 * DISABLED LISTENER - Items should ONLY stack when /stack command is used
 *
 * Previously this listener automatically applied max stack sizes to items
 * during pickup, crafting, inventory clicks, etc.
 *
 * NOW: No automatic stacking! Players must use /stack command manually.
 */
public class StackingListener implements Listener {

    // NO EVENTS REGISTERED
    // All auto-stacking functionality removed
    // Items will only stack when /stack command is explicitly used

}
