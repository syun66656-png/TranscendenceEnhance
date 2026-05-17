package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import com.yourserver.traderestrict.manager.RestrictedItemReturner;
import com.yourserver.traderestrict.storage.PersistentItemStore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerQuitListener implements Listener {

    private final Plugin plugin;
    private final PersistentItemStore itemStore;
    private final RestrictedItemMarker marker;
    private final RestrictedItemReturner returner;
    private final Map<UUID, BukkitTask> deliveryTasks = new ConcurrentHashMap<>();

    public PlayerQuitListener(
            Plugin plugin,
            PersistentItemStore itemStore,
            RestrictedItemMarker marker,
            RestrictedItemReturner returner
    ) {
        this.plugin = plugin;
        this.itemStore = itemStore;
        this.marker = marker;
        this.returner = returner;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopDeliveryTask(player.getUniqueId());

        List<ItemStack> restrictedItems = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!RestrictedItemChecker.isRestricted(item)) {
                continue;
            }
            marker.tagOwner(player, item);
            restrictedItems.add(item.clone());
            player.getInventory().setItem(slot, null);
        }

        ItemStack cursor = player.getItemOnCursor();
        if (RestrictedItemChecker.isRestricted(cursor)) {
            marker.tagOwner(player, cursor);
            restrictedItems.add(cursor.clone());
            player.setItemOnCursor(null);
        }

        if (!restrictedItems.isEmpty()) {
            itemStore.addItems(player.getUniqueId(), restrictedItems);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tagInventory(player);
        if (itemStore.hasItems(player.getUniqueId())) {
            startDeliveryTask(player);
        }
    }

    private void startDeliveryTask(Player player) {
        UUID uuid = player.getUniqueId();
        stopDeliveryTask(uuid);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopDeliveryTask(uuid);
                return;
            }
            List<ItemStack> stored = itemStore.drainItems(uuid);
            if (!stored.isEmpty()) {
                returner.returnToPlayer(player, stored);
            }
            if (!itemStore.hasItems(uuid)) {
                stopDeliveryTask(uuid);
            }
        }, 1L, 40L);
        deliveryTasks.put(uuid, task);
    }

    private void stopDeliveryTask(UUID uuid) {
        BukkitTask task = deliveryTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void tagInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && RestrictedItemChecker.isRestricted(item)) {
                marker.tagOwner(player, item);
            }
        }
    }
}
