package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.Optional;
import java.util.UUID;

public final class EntityPickupListener implements Listener {

    private final RestrictedItemMarker marker;

    public EntityPickupListener(RestrictedItemMarker marker) {
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!RestrictedItemChecker.isRestricted(event.getItem().getItemStack())) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        Optional<UUID> owner = marker.getOwner(event.getItem().getItemStack());
        if (owner.isEmpty() || !owner.get().equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
