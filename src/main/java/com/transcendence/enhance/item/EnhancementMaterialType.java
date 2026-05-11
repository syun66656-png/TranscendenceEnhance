package com.transcendence.enhance.item;

public enum EnhancementMaterialType {
    TRANSCENDENCE_STONE("Transcendence_Stone", "transcendence_stone", "초월석"),
    ENHANCEMENT_STONE("Enhancement_Stone", "enhancement_stone", "강화석");

    private final String configSection;
    private final String keyName;
    private final String displayName;

    EnhancementMaterialType(String configSection, String keyName, String displayName) {
        this.configSection = configSection;
        this.keyName = keyName;
        this.displayName = displayName;
    }

    public String configPath() {
        return "Items." + configSection;
    }

    public String keyName() {
        return keyName;
    }

    public String displayName() {
        return displayName;
    }
}
