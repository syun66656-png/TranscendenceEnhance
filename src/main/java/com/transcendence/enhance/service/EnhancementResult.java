package com.transcendence.enhance.service;

import org.bukkit.inventory.ItemStack;

public final class EnhancementResult {

    private final boolean valid;
    private final boolean success;
    private final ItemStack resultItem;

    private EnhancementResult(boolean valid, boolean success, ItemStack resultItem) {
        this.valid = valid;
        this.success = success;
        this.resultItem = resultItem;
    }

    public static EnhancementResult success(ItemStack resultItem) {
        return new EnhancementResult(true, true, resultItem);
    }

    public static EnhancementResult fail(ItemStack resultItem) {
        return new EnhancementResult(true, false, resultItem);
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

    public ItemStack getResultItem() {
        return resultItem;
    }
}
