package com.transcendence.enhance.service;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class EnhancementService {

    private final Plugin plugin;

    public EnhancementService(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnhanceableTool(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }

        ToolTier tier = ToolTier.fromMaterial(stack.getType());
        return tier != null && tier.next() != null;
    }

    public EnhancementResult enhance(ItemStack original) {
        ToolTier currentTier = ToolTier.fromMaterial(original.getType());
        if (currentTier == null || currentTier.next() == null) {
            return EnhancementResult.invalid();
        }

        ToolTier nextTier = currentTier.next();
        int successRate = readRate(currentTier, nextTier);
        boolean success = roll(successRate);

        if (!success) {
            return EnhancementResult.fail();
        }

        Material upgradedType = convertToNextMaterial(original.getType(), currentTier, nextTier);
        if (upgradedType == null) {
            return EnhancementResult.invalid();
        }

        ItemStack upgraded = original.clone();
        upgraded.setType(upgradedType);
        return EnhancementResult.success(upgraded);
    }

    public int getCost(ItemStack original) {
        ToolTier currentTier = ToolTier.fromMaterial(original.getType());
        if (currentTier == null || currentTier.next() == null) {
            return 0;
        }
        ToolTier nextTier = currentTier.next();
        String path = "Enhance_Table." + currentTier.name() + "_TO_" + nextTier.name() + ".Cost";
        return Math.max(0, plugin.getConfig().getInt(path, 0));
    }

    private int readRate(ToolTier current, ToolTier next) {
        FileConfiguration config = plugin.getConfig();
        String path = "Enhance_Table." + current.name() + "_TO_" + next.name() + ".Success_Rate";
        return Math.max(0, Math.min(100, config.getInt(path, 0)));
    }

    private boolean roll(int successRate) {
        return ThreadLocalRandom.current().nextInt(100) < successRate;
    }

    private Material convertToNextMaterial(Material currentType, ToolTier currentTier, ToolTier nextTier) {
        String sourcePrefix = currentTier.materialPrefix + "_";
        String targetPrefix = nextTier.materialPrefix + "_";
        String materialName = currentType.name();
        if (!materialName.startsWith(sourcePrefix)) {
            return null;
        }

        String suffix = materialName.substring(sourcePrefix.length());
        try {
            return Material.valueOf((targetPrefix + suffix).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private enum ToolTier {
        WOOD("WOODEN"),
        STONE("STONE"),
        IRON("IRON"),
        DIAMOND("DIAMOND"),
        NETHERITE("NETHERITE");

        private final String materialPrefix;

        ToolTier(String materialPrefix) {
            this.materialPrefix = materialPrefix;
        }

        public static ToolTier fromMaterial(Material material) {
            String name = material.name();
            for (ToolTier tier : values()) {
                if (name.startsWith(tier.materialPrefix + "_")) {
                    return tier;
                }
            }
            return null;
        }

        public ToolTier next() {
            int idx = ordinal();
            ToolTier[] values = values();
            if (idx + 1 >= values.length) {
                return null;
            }
            return values[idx + 1];
        }

    }
}
