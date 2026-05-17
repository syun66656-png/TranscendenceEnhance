package com.yourserver.traderestrict.checker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RestrictedItemChecker {

    private static final String DEFAULT_KEYWORD = "거래 금지";
    private static String keyword = DEFAULT_KEYWORD;

    private RestrictedItemChecker() {
    }

    public static void configure(String configuredKeyword) {
        if (configuredKeyword == null || configuredKeyword.isBlank()) {
            keyword = DEFAULT_KEYWORD;
            return;
        }
        keyword = stripColors(configuredKeyword);
    }

    public static boolean isRestricted(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        Component displayName = meta.displayName();
        if (displayName != null && stripColors(PlainTextComponentSerializer.plainText().serialize(displayName)).contains(keyword)) {
            return true;
        }

        return stripColors(meta.getDisplayName()).contains(keyword);
    }

    private static String stripColors(String input) {
        if (input == null) {
            return "";
        }
        String translated = ChatColor.translateAlternateColorCodes('&', input);
        String stripped = ChatColor.stripColor(translated);
        return stripped == null ? "" : stripped;
    }
}
