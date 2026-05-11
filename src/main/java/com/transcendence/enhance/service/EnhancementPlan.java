package com.transcendence.enhance.service;

public final class EnhancementPlan {

    private final EnhancementType type;
    private final String fromDisplayName;
    private final String toDisplayName;
    private final int currentLevel;
    private final int nextLevel;
    private final int successRate;
    private final int cost;
    private final int stoneAmount;

    private EnhancementPlan(
            EnhancementType type,
            String fromDisplayName,
            String toDisplayName,
            int currentLevel,
            int nextLevel,
            int successRate,
            int cost,
            int stoneAmount
    ) {
        this.type = type;
        this.fromDisplayName = fromDisplayName;
        this.toDisplayName = toDisplayName;
        this.currentLevel = currentLevel;
        this.nextLevel = nextLevel;
        this.successRate = successRate;
        this.cost = cost;
        this.stoneAmount = stoneAmount;
    }

    public static EnhancementPlan transcendence(String fromDisplayName, String toDisplayName, int successRate, int cost, int stoneAmount) {
        return new EnhancementPlan(EnhancementType.TRANSCENDENCE, fromDisplayName, toDisplayName, -1, -1, successRate, cost, stoneAmount);
    }

    public static EnhancementPlan enchantment(EnhancementType type, int currentLevel, int nextLevel, int successRate, int cost, int stoneAmount) {
        return new EnhancementPlan(type, "", "", currentLevel, nextLevel, successRate, cost, stoneAmount);
    }

    public EnhancementType type() {
        return type;
    }

    public String fromDisplayName() {
        return fromDisplayName;
    }

    public String toDisplayName() {
        return toDisplayName;
    }

    public int currentLevel() {
        return currentLevel;
    }

    public int nextLevel() {
        return nextLevel;
    }

    public int successRate() {
        return successRate;
    }

    public int cost() {
        return cost;
    }

    public int stoneAmount() {
        return stoneAmount;
    }
}
