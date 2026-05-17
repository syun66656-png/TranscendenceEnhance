package com.yourserver.traderestrict.manager;

import com.yourserver.traderestrict.storage.PersistentItemStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RestrictedItemReturner {

    private final Plugin plugin;
    private final PersistentItemStore itemStore;
    private final RestrictedItemMarker marker;

    public RestrictedItemReturner(Plugin plugin, PersistentItemStore itemStore, RestrictedItemMarker marker) {
        this.plugin = plugin;
        this.itemStore = itemStore;
        this.marker = marker;
    }

    public void returnToOwner(UUID owner, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        List<ItemStack> items = List.of(item.clone());
        Player player = owner == null ? null : Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) {
            if (owner != null) {
                itemStore.addItems(owner, items);
            }
            return;
        }
        returnToPlayer(player, items);
    }

    public void returnToPlayer(Player player, Collection<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) {
            return;
        }
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            ItemStack clone = item.clone();
            marker.tagOwner(player, clone);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(clone);
            leftovers.addAll(leftover.values());
        }
        if (!leftovers.isEmpty()) {
            itemStore.addItems(player.getUniqueId(), leftovers);
        }
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }
}
