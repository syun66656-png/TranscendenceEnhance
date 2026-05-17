package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockPlaceListener implements Listener {

    private final RestrictedItemMarker marker;

    public BlockPlaceListener(RestrictedItemMarker marker) {
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!RestrictedItemChecker.isRestricted(event.getItemInHand())) {
            return;
        }
        marker.tagOwner(event.getPlayer(), event.getItemInHand());
        event.setCancelled(true);
    }
}
