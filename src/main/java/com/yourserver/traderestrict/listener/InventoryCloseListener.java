package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.InventoryAccess;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import com.yourserver.traderestrict.manager.RestrictedItemReturner;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class InventoryCloseListener implements Listener {

    private final Plugin plugin;
    private final InventoryAccess inventoryAccess;
    private final RestrictedItemMarker marker;
    private final RestrictedItemReturner returner;

    public InventoryCloseListener(
            Plugin plugin,
            InventoryAccess inventoryAccess,
            RestrictedItemMarker marker,
            RestrictedItemReturner returner
    ) {
        this.plugin = plugin;
        this.inventoryAccess = inventoryAccess;
        this.marker = marker;
        this.returner = returner;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        recoverCursor(player);

        Inventory top = event.getView().getTopInventory();
        if (inventoryAccess.isInventoryAllowed(player, top)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> recoverExternalInventory(player, top));
    }

    private void recoverCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (!RestrictedItemChecker.isRestricted(cursor)) {
            return;
        }
        marker.tagOwner(player, cursor);
        player.setItemOnCursor(null);
        returner.returnToOwner(marker.getOwner(cursor).orElse(player.getUniqueId()), cursor);
    }

    private void recoverExternalInventory(Player player, Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR || !RestrictedItemChecker.isRestricted(item)) {
                continue;
            }
            UUID owner = marker.getOwner(item).orElse(player.getUniqueId());
            inventory.setItem(slot, null);
            returner.returnToOwner(owner, item);
        }
    }
}
