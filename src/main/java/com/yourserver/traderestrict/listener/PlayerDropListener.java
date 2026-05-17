package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class PlayerDropListener implements Listener {

    private final RestrictedItemMarker marker;

    public PlayerDropListener(RestrictedItemMarker marker) {
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!RestrictedItemChecker.isRestricted(event.getItemDrop().getItemStack())) {
            return;
        }
        marker.tagOwner(event.getPlayer(), event.getItemDrop().getItemStack());
        event.setCancelled(true);
    }
}
