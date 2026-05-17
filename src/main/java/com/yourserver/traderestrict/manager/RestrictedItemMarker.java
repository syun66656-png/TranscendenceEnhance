package com.yourserver.traderestrict.manager;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

public final class RestrictedItemMarker {

    private final NamespacedKey ownerKey;

    public RestrictedItemMarker(Plugin plugin) {
        this.ownerKey = new NamespacedKey(plugin, "restricted_owner");
    }

    public void tagOwner(Player owner, ItemStack item) {
        if (owner == null || item == null || item.getType() == Material.AIR || !RestrictedItemChecker.isRestricted(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        item.setItemMeta(meta);
    }

    public Optional<UUID> getOwner(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String rawUuid = container.get(ownerKey, PersistentDataType.STRING);
        if (rawUuid == null || rawUuid.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(rawUuid));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
