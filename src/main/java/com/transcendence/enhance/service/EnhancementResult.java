package com.transcendence.enhance.service;

import org.bukkit.inventory.ItemStack;

public final class EnhancementResult {

    private final boolean valid;
    private final boolean success;
    private final ItemStack upgradedItem;

    private EnhancementResult(boolean valid, boolean success, ItemStack upgradedItem) {
        this.valid = valid;
        this.success = success;
        this.upgradedItem = upgradedItem;
    }

    public static EnhancementResult success(ItemStack upgradedItem) {
        return new EnhancementResult(true, true, upgradedItem);
    }

    public static EnhancementResult fail() {
        return new EnhancementResult(true, false, null);
    }

    public static EnhancementResult invalid() {
        return new EnhancementResult(false, false, null);
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isSuccess() {
        return success;
    }

    public ItemStack getUpgradedItem() {
        return upgradedItem;
    }
}
