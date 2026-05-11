package com.transcendence.enhance.service;

import com.transcendence.enhance.item.EnhancementMaterialType;

public enum EnhancementType {
    TRANSCENDENCE("Transcendence", "GUI_Title_Transcendence", "초월 강화", EnhancementMaterialType.TRANSCENDENCE_STONE),
    EFFICIENCY("Efficiency", "GUI_Title_Efficiency", "효율 강화", EnhancementMaterialType.ENHANCEMENT_STONE),
    FORTUNE("Fortune", "GUI_Title_Fortune", "행운 강화", EnhancementMaterialType.ENHANCEMENT_STONE);

    private final String configPath;
    private final String titleKey;
    private final String displayName;
    private final EnhancementMaterialType materialType;

    EnhancementType(String configPath, String titleKey, String displayName, EnhancementMaterialType materialType) {
        this.configPath = configPath;
        this.titleKey = titleKey;
        this.displayName = displayName;
        this.materialType = materialType;
    }

    public String configPath() {
        return configPath;
    }

    public String titlePath() {
        return "Settings." + titleKey;
    }

    public String displayName() {
        return displayName;
    }

    public EnhancementMaterialType materialType() {
        return materialType;
    }
}
