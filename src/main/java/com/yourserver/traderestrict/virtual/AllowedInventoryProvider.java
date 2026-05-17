package com.yourserver.traderestrict.virtual;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public interface AllowedInventoryProvider {

    boolean isAllowed(Player player, Inventory inventory);
}
