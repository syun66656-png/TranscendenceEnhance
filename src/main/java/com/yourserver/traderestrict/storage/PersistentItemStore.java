package com.yourserver.traderestrict.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class PersistentItemStore {

    private final Plugin plugin;
    private final File file;
    private YamlConfiguration data;

    public PersistentItemStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "restricted-items.yml");
        reload();
    }

    public void reload() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean hasItems(UUID owner) {
        return !getItems(owner).isEmpty();
    }

    public List<ItemStack> getItems(UUID owner) {
        if (owner == null) {
            return Collections.emptyList();
        }
        List<?> rawItems = data.getList(path(owner), Collections.emptyList());
        List<ItemStack> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (rawItem instanceof ItemStack item) {
                items.add(item.clone());
            }
        }
        return items;
    }

    public void addItems(UUID owner, Collection<ItemStack> items) {
        if (owner == null || items == null || items.isEmpty()) {
            return;
        }
        List<ItemStack> stored = getItems(owner);
        for (ItemStack item : items) {
            if (item != null) {
                stored.add(item.clone());
            }
        }
        data.set(path(owner), stored);
        save();
    }

    public List<ItemStack> drainItems(UUID owner) {
        List<ItemStack> items = getItems(owner);
        if (!items.isEmpty()) {
            data.set(path(owner), null);
            save();
        }
        return items;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save restricted item store.", ex);
        }
    }

    private String path(UUID owner) {
        return "players." + owner;
    }
}
