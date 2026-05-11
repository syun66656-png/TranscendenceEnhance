package com.transcendence.enhance.service;

import com.transcendence.enhance.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class EnhancementService {

    private final Plugin plugin;

    public EnhancementService(Plugin plugin) {
        this.plugin = plugin;
    }

    public Optional<EnhancementPlan> createPlan(EnhancementType type, ItemStack tool) {
        if (!hasSingleNamedTool(tool)) {
            return Optional.empty();
        }
        return switch (type) {
            case TRANSCENDENCE -> createTranscendencePlan(tool);
            case EFFICIENCY, FORTUNE -> createEnchantmentPlan(type, tool);
        };
    }

    public boolean isAtMaximum(EnhancementType type, ItemStack tool) {
        if (type == EnhancementType.TRANSCENDENCE || !isRegisteredEnhanceableTool(tool)) {
            return false;
        }
        return tool.getEnchantmentLevel(enchantment(type)) >= maxLevel(type);
    }

    public EnhancementResult enhance(EnhancementPlan plan, ItemStack original) {
        if (plan == null || original == null || original.getType() == Material.AIR) {
            return EnhancementResult.invalid();
        }
        if (!roll(plan.successRate())) {
            return EnhancementResult.fail(original.clone());
        }

        ItemStack result = original.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return EnhancementResult.invalid();
        }

        if (plan.type() == EnhancementType.TRANSCENDENCE) {
            meta.displayName(TextUtil.color(plan.toDisplayName()));
        } else {
            meta.addEnchant(enchantment(plan.type()), plan.nextLevel(), true);
        }
        result.setItemMeta(meta);
        return EnhancementResult.success(result);
    }

    private Optional<EnhancementPlan> createTranscendencePlan(ItemStack tool) {
        String displayName = TextUtil.legacyDisplayName(tool);
        List<Map<?, ?>> routes = plugin.getConfig().getMapList("Transcendence.Routes");
        for (Map<?, ?> route : routes) {
            String from = TextUtil.toLegacy(readString(route, "From"));
            if (!displayName.equals(from)) {
                continue;
            }
            String to = TextUtil.toLegacy(readString(route, "To"));
            int successRate = clampRate(readInt(route, "Success_Rate", 0));
            int cost = Math.max(0, readInt(route, "Cost", 0));
            int stoneAmount = Math.max(1, readInt(route, "Stone_Amount", 1));
            return Optional.of(EnhancementPlan.transcendence(from, to, successRate, cost, stoneAmount));
        }
        return Optional.empty();
    }

    private Optional<EnhancementPlan> createEnchantmentPlan(EnhancementType type, ItemStack tool) {
        if (!isRegisteredEnhanceableTool(tool)) {
            return Optional.empty();
        }

        int currentLevel = tool.getEnchantmentLevel(enchantment(type));
        int maxLevel = maxLevel(type);
        if (currentLevel >= maxLevel) {
            return Optional.empty();
        }

        int nextLevel = currentLevel + 1;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(type.configPath() + "." + currentLevel + "_TO_" + nextLevel);
        if (section == null) {
            return Optional.empty();
        }

        int successRate = clampRate(section.getInt("Success_Rate", 0));
        int cost = Math.max(0, section.getInt("Cost", 0));
        int stoneAmount = Math.max(1, section.getInt("Stone_Amount", 1));
        return Optional.of(EnhancementPlan.enchantment(type, currentLevel, nextLevel, successRate, cost, stoneAmount));
    }

    private boolean isRegisteredEnhanceableTool(ItemStack tool) {
        if (!hasSingleNamedTool(tool)) {
            return false;
        }

        String displayName = TextUtil.legacyDisplayName(tool);
        List<Map<?, ?>> tools = plugin.getConfig().getMapList("Enhanceable_Tools");
        for (Map<?, ?> entry : tools) {
            String expected = TextUtil.toLegacy(readString(entry, "DisplayName"));
            if (displayName.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSingleNamedTool(ItemStack stack) {
        return stack != null
                && stack.getType() != Material.AIR
                && stack.getAmount() == 1
                && !TextUtil.legacyDisplayName(stack).isEmpty();
    }

    private int readInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String readString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private int clampRate(int rate) {
        return Math.max(0, Math.min(100, rate));
    }

    private boolean roll(int successRate) {
        return ThreadLocalRandom.current().nextInt(100) < successRate;
    }

    private Enchantment enchantment(EnhancementType type) {
        return switch (type) {
            case EFFICIENCY -> Enchantment.EFFICIENCY;
            case FORTUNE -> Enchantment.FORTUNE;
            case TRANSCENDENCE -> throw new IllegalArgumentException("Transcendence has no enchantment");
        };
    }

    private int maxLevel(EnhancementType type) {
        return switch (type) {
            case EFFICIENCY -> 5;
            case FORTUNE -> 3;
            case TRANSCENDENCE -> 0;
        };
    }
}
