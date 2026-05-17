package com.yourserver.traderestrict;

import com.yourserver.traderestrict.checker.RestrictedItemChecker;
import com.yourserver.traderestrict.listener.BlockPlaceListener;
import com.yourserver.traderestrict.listener.EntityPickupListener;
import com.yourserver.traderestrict.listener.InventoryClickListener;
import com.yourserver.traderestrict.listener.InventoryCloseListener;
import com.yourserver.traderestrict.listener.InventoryDragListener;
import com.yourserver.traderestrict.listener.InventoryMoveListener;
import com.yourserver.traderestrict.listener.PlayerDeathListener;
import com.yourserver.traderestrict.listener.PlayerDropListener;
import com.yourserver.traderestrict.listener.PlayerPickupListener;
import com.yourserver.traderestrict.listener.PlayerQuitListener;
import com.yourserver.traderestrict.manager.GroundItemManager;
import com.yourserver.traderestrict.manager.InventoryAccess;
import com.yourserver.traderestrict.manager.RestrictedItemMarker;
import com.yourserver.traderestrict.manager.RestrictedItemReturner;
import com.yourserver.traderestrict.storage.PersistentItemStore;
import com.yourserver.traderestrict.virtual.AllowedInventoryProvider;
import com.yourserver.traderestrict.virtual.ConfiguredAllowedInventoryProvider;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradeRestrictPlugin extends JavaPlugin {

    private PersistentItemStore itemStore;
    private GroundItemManager groundItemManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RestrictedItemChecker.configure(getConfig().getString("restrict-keyword", "거래 금지"));

        AllowedInventoryProvider allowedInventoryProvider = new ConfiguredAllowedInventoryProvider(this);
        InventoryAccess inventoryAccess = new InventoryAccess(allowedInventoryProvider);
        RestrictedItemMarker marker = new RestrictedItemMarker(this);
        this.itemStore = new PersistentItemStore(this);
        RestrictedItemReturner returner = new RestrictedItemReturner(this, itemStore, marker);
        this.groundItemManager = new GroundItemManager(this, marker, returner);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new InventoryClickListener(inventoryAccess, marker), this);
        pluginManager.registerEvents(new InventoryDragListener(inventoryAccess, marker), this);
        pluginManager.registerEvents(new InventoryMoveListener(), this);
        pluginManager.registerEvents(new InventoryCloseListener(this, inventoryAccess, marker, returner), this);
        pluginManager.registerEvents(new PlayerDropListener(marker), this);
        pluginManager.registerEvents(new PlayerDeathListener(this, marker, returner), this);
        pluginManager.registerEvents(new EntityPickupListener(marker), this);
        pluginManager.registerEvents(new PlayerPickupListener(marker), this);
        pluginManager.registerEvents(new PlayerQuitListener(this, itemStore, marker, returner), this);
        pluginManager.registerEvents(new BlockPlaceListener(marker), this);
        pluginManager.registerEvents(groundItemManager, this);

        groundItemManager.scanExistingGroundItems();
    }

    @Override
    public void onDisable() {
        if (itemStore != null) {
            itemStore.reload();
        }
    }
}
