package com.transcendence.enhance.item;

import com.transcendence.enhance.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class EnhancementMaterialFactory {

    private final Plugin plugin;
    private final Map<EnhancementMaterialType, NamespacedKey> keys = new EnumMap<>(EnhancementMaterialType.class);

    public EnhancementMaterialFactory(Plugin plugin) {
        this.plugin = plugin;
        for (EnhancementMaterialType type : EnhancementMaterialType.values()) {
            keys.put(type, new NamespacedKey(plugin, type.keyName()));
        }
    }

    public ItemStack create(EnhancementMaterialType type, int amount) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(type.configPath());
        Material material = safeMaterial(section != null ? section.getString("Material") : null, defaultMaterial(type));

        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && section != null) {
            meta.displayName(TextUtil.color(section.getString("Name", defaultName(type))));
            meta.lore(TextUtil.colorList(section.getStringList("Lore")));
            int modelData = section.getInt("CustomModelData", -1);
            if (modelData >= 0) {
                meta.setCustomModelData(modelData);
            }
            meta.getPersistentDataContainer().set(key(type), PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isMaterial(EnhancementMaterialType type, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key(type), PersistentDataType.BYTE);
    }

    public NamespacedKey key(EnhancementMaterialType type) {
        return keys.get(type);
    }

    private Material defaultMaterial(EnhancementMaterialType type) {
        return switch (type) {
            case TRANSCENDENCE_STONE -> Material.PRISMARINE_SHARD;
            case ENHANCEMENT_STONE -> Material.PRISMARINE_CRYSTALS;
        };
    }

    private String defaultName(EnhancementMaterialType type) {
        return switch (type) {
            case TRANSCENDENCE_STONE -> "&b&l[초월석]";
            case ENHANCEMENT_STONE -> "&e&l[강화석]";
        };
    }

    private Material safeMaterial(String input, Material fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
