package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

import java.util.Optional;
import java.util.UUID;

public final class PlayerPickupListener implements Listener {

    private final RestrictedItemMarker marker;

    public PlayerPickupListener(RestrictedItemMarker marker) {
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (!RestrictedItemChecker.isRestricted(event.getItem().getItemStack())) {
            return;
        }
        Optional<UUID> owner = marker.getOwner(event.getItem().getItemStack());
        if (owner.isEmpty() || !owner.get().equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
