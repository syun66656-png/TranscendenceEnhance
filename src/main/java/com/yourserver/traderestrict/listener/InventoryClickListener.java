package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.InventoryAccess;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class InventoryClickListener implements Listener {

    private final InventoryAccess inventoryAccess;
    private final RestrictedItemMarker marker;

    public InventoryClickListener(InventoryAccess inventoryAccess, RestrictedItemMarker marker) {
        this.inventoryAccess = inventoryAccess;
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        tagIfRestricted(player, cursor);
        tagIfRestricted(player, current);

        if (isDropAction(event.getAction(), event.getClick())) {
            if (RestrictedItemChecker.isRestricted(cursor) || RestrictedItemChecker.isRestricted(current)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (RestrictedItemChecker.isRestricted(current)
                    && !inventoryAccess.isShiftClickDestinationAllowed(player, event)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            handleNumberKeyClick(event, player, current);
            return;
        }

        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            handleOffhandSwap(event, player, current);
            return;
        }

        if (RestrictedItemChecker.isRestricted(cursor)
                && !inventoryAccess.isClickedInventoryAllowed(player, event)) {
            event.setCancelled(true);
        }
    }

    private void handleNumberKeyClick(InventoryClickEvent event, Player player, ItemStack current) {
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton < 0) {
            return;
        }
        ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
        tagIfRestricted(player, hotbarItem);

        if (RestrictedItemChecker.isRestricted(hotbarItem)
                && !inventoryAccess.isClickedInventoryAllowed(player, event)) {
            event.setCancelled(true);
            return;
        }

        if (RestrictedItemChecker.isRestricted(current)) {
            marker.tagOwner(player, current);
        }
    }

    private void handleOffhandSwap(InventoryClickEvent event, Player player, ItemStack current) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        tagIfRestricted(player, offhand);
        tagIfRestricted(player, current);

        if (RestrictedItemChecker.isRestricted(offhand)
                && !inventoryAccess.isClickedInventoryAllowed(player, event)) {
            event.setCancelled(true);
        }
    }

    private boolean isDropAction(InventoryAction action, ClickType click) {
        return action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP;
    }

    private void tagIfRestricted(Player player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR && RestrictedItemChecker.isRestricted(item)) {
            marker.tagOwner(player, item);
        }
    }
}
