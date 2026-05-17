package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public final class InventoryMoveListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (RestrictedItemChecker.isRestricted(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
