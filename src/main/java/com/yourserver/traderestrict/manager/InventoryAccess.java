package com.yourserver.traderestrict.manager;

import com.yourserver.traderestrict.virtual.AllowedInventoryProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class InventoryAccess {

    private final AllowedInventoryProvider allowedInventoryProvider;

    public InventoryAccess(AllowedInventoryProvider allowedInventoryProvider) {
        this.allowedInventoryProvider = allowedInventoryProvider;
    }

    public boolean isAllowedSlot(Player player, InventoryView view, int rawSlot) {
        Inventory inventory = inventoryForRawSlot(view, rawSlot);
        return inventory != null && allowedInventoryProvider.isAllowed(player, inventory);
    }

    public boolean isClickedInventoryAllowed(Player player, InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        return clicked != null && allowedInventoryProvider.isAllowed(player, clicked);
    }

    public boolean isShiftClickDestinationAllowed(Player player, InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return false;
        }
        InventoryView view = event.getView();
        Inventory destination = event.getRawSlot() < view.getTopInventory().getSize()
                ? view.getBottomInventory()
                : view.getTopInventory();
        return allowedInventoryProvider.isAllowed(player, destination);
    }

    public boolean isInventoryAllowed(Player player, Inventory inventory) {
        return allowedInventoryProvider.isAllowed(player, inventory);
    }

    private Inventory inventoryForRawSlot(InventoryView view, int rawSlot) {
        if (rawSlot < 0) {
            return null;
        }
        Inventory inventory = view.getInventory(rawSlot);
        if (inventory != null) {
            return inventory;
        }
        if (rawSlot < view.getTopInventory().getSize()) {
            return view.getTopInventory();
        }
        if (rawSlot < view.countSlots()) {
            return view.getBottomInventory();
        }
        return null;
    }
}
