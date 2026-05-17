package com.yourserver.traderestrict.listener;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import com.yourserver.traderestrict.manager.RestrictedItemReturner;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDeathListener implements Listener {

    private final Plugin plugin;
    private final RestrictedItemMarker marker;
    private final RestrictedItemReturner returner;
    private final Map<UUID, List<ItemStack>> pendingRespawnItems = new ConcurrentHashMap<>();

    public PlayerDeathListener(Plugin plugin, RestrictedItemMarker marker, RestrictedItemReturner returner) {
        this.plugin = plugin;
        this.marker = marker;
        this.returner = returner;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<ItemStack> restrictedItems = new ArrayList<>();

        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (!RestrictedItemChecker.isRestricted(item)) {
                continue;
            }
            marker.tagOwner(player, item);
            restrictedItems.add(item.clone());
            iterator.remove();
        }

        if (!restrictedItems.isEmpty()) {
            pendingRespawnItems
                    .computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>())
                    .addAll(restrictedItems);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> items = pendingRespawnItems.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> returner.returnToPlayer(player, nonAir(items)));
    }

    private List<ItemStack> nonAir(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                result.add(item);
            }
        }
        return result;
    }
}
