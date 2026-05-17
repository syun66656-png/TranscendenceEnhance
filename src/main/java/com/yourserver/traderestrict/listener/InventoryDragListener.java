package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.InventoryAccess;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class InventoryDragListener implements Listener {

    private final InventoryAccess inventoryAccess;
    private final RestrictedItemMarker marker;

    public InventoryDragListener(InventoryAccess inventoryAccess, RestrictedItemMarker marker) {
        this.inventoryAccess = inventoryAccess;
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack dragged = event.getOldCursor();
        if (!RestrictedItemChecker.isRestricted(dragged)) {
            return;
        }

        marker.tagOwner(player, dragged);
        for (int rawSlot : event.getRawSlots()) {
            if (!inventoryAccess.isAllowedSlot(player, event.getView(), rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
