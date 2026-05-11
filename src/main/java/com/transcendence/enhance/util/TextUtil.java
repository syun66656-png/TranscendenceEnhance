package com.transcendence.enhance.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private TextUtil() {
    }

    public static Component color(String input) {
        return LEGACY_SECTION.deserialize(toLegacy(input));
    }

    public static List<Component> colorList(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(color(line));
        }
        return components;
    }

    public static String toLegacy(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('&', '§');
    }

    public static String legacyDisplayName(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return "";
        }
        return LEGACY_SECTION.serialize(meta.displayName());
    }
}
