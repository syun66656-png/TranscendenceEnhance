package com.transcendence.enhance.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TranscendenceStoneFactory {

    private final Plugin plugin;
    private final NamespacedKey stoneKey;

    public TranscendenceStoneFactory(Plugin plugin, NamespacedKey stoneKey) {
        this.plugin = plugin;
        this.stoneKey = stoneKey;
    }

    public ItemStack createStone(int amount) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("Items.Transcendence_Stone");
        Material material = safeMaterial(section != null ? section.getString("Material") : null, Material.PRISMARINE_SHARD);
        String name = color(section != null ? section.getString("Name", "&b&l[초월석]") : "&b&l[초월석]");
        int modelData = section != null ? section.getInt("CustomModelData", 10001) : 10001;
        List<String> lore = section != null ? color(section.getStringList("Lore")) : List.of(color("&f도구를 다음 단계로 강화합니다."));

        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.setCustomModelData(modelData);
            meta.getPersistentDataContainer().set(stoneKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String color(String input) {
        return input == null ? "" : input.replace("&", "§");
    }

    private List<String> color(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
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
