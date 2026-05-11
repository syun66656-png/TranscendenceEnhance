package com.transcendence.enhance.gui;

import com.transcendence.enhance.service.EnhancementType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

public final class TranscendenceInventoryHolder implements InventoryHolder {

    private final EnhancementType type;

    public TranscendenceInventoryHolder(EnhancementType type) {
        this.type = type;
    }

    public EnhancementType type() {
        return type;
    }

    @Override
    public @Nullable Inventory getInventory() {
        return null;
    }
}
