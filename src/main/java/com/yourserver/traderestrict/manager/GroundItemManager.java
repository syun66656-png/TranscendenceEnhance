package com.yourserver.traderestrict.manager;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class GroundItemManager implements Listener {

    private final Plugin plugin;
    private final RestrictedItemMarker marker;
    private final RestrictedItemReturner returner;

    public GroundItemManager(Plugin plugin, RestrictedItemMarker marker, RestrictedItemReturner returner) {
        this.plugin = plugin;
        this.marker = marker;
        this.returner = returner;
    }

    public void scanExistingGroundItems() {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Item item) {
                        recoverGroundItem(item);
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!RestrictedItemChecker.isRestricted(item.getItemStack())) {
            return;
        }
        event.setCancelled(true);
        recoverGroundItem(item);
    }

    private void recoverGroundItem(Item item) {
        ItemStack stack = item.getItemStack();
        if (!RestrictedItemChecker.isRestricted(stack)) {
            return;
        }
        UUID owner = marker.getOwner(stack).orElse(null);
        item.remove();
        if (owner != null) {
            returner.returnToOwner(owner, stack);
        }
    }
}
