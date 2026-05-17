package com.yourserver.traderestrict.virtual;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class ConfiguredAllowedInventoryProvider implements AllowedInventoryProvider {

    private final Plugin plugin;
    private final Logger logger;
    private final String virtualStoragePluginName;
    private final String mode;
    private final String apiClassName;
    private final String apiMethodName;
    private final boolean debug;

    public ConfiguredAllowedInventoryProvider(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        FileConfiguration config = plugin.getConfig();
        this.virtualStoragePluginName = config.getString("virtual-storage.plugin-name", "VirtualStorage");
        this.mode = config.getString("virtual-storage.mode", "holder").toLowerCase(Locale.ROOT);
        this.apiClassName = config.getString("virtual-storage.api-class", "");
        this.apiMethodName = config.getString("virtual-storage.api-method", "isVirtualInventory");
        this.debug = config.getBoolean("debug", false);
    }

    @Override
    public boolean isAllowed(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return false;
        }
        if (isOwnPlayerInventory(player, inventory)) {
            return true;
        }
        if (!isVirtualStoragePresent()) {
            return false;
        }
        return isAllowedVirtualInventory(player, inventory);
    }

    private boolean isOwnPlayerInventory(Player player, Inventory inventory) {
        if (inventory instanceof PlayerInventory playerInventory) {
            return Objects.equals(playerInventory.getHolder(), player);
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Player owner && owner.getUniqueId().equals(player.getUniqueId());
    }

    private boolean isVirtualStoragePresent() {
        if (virtualStoragePluginName == null || virtualStoragePluginName.isBlank()) {
            return false;
        }
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        Plugin virtualStorage = pluginManager.getPlugin(virtualStoragePluginName);
        return virtualStorage != null && virtualStorage.isEnabled();
    }

    private boolean isAllowedVirtualInventory(Player player, Inventory inventory) {
        if ("api".equals(mode) && isAllowedByApi(player, inventory)) {
            return true;
        }
        return isAllowedByHolder(player, inventory);
    }

    private boolean isAllowedByApi(Player player, Inventory inventory) {
        if (apiClassName == null || apiClassName.isBlank()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName(apiClassName);
            for (Method method : apiClass.getMethods()) {
                if (!method.getName().equals(apiMethodName) || method.getReturnType() != boolean.class) {
                    continue;
                }
                Object result = invokeApiMethod(method, player, inventory);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ex) {
            debug("VirtualStorage API lookup failed: " + ex.getMessage());
        }
        return false;
    }

    private Object invokeApiMethod(Method method, Player player, Inventory inventory) throws ReflectiveOperationException {
        Object target = Modifier.isStatic(method.getModifiers()) ? null : plugin.getServer().getPluginManager().getPlugin(virtualStoragePluginName);
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 2
                && parameterTypes[0].isAssignableFrom(Inventory.class)
                && parameterTypes[1].isAssignableFrom(Player.class)) {
            return method.invoke(target, inventory, player);
        }
        if (parameterTypes.length == 2
                && parameterTypes[0].isAssignableFrom(Player.class)
                && parameterTypes[1].isAssignableFrom(Inventory.class)) {
            return method.invoke(target, player, inventory);
        }
        if (parameterTypes.length == 2
                && parameterTypes[0].isAssignableFrom(Inventory.class)
                && parameterTypes[1].isAssignableFrom(UUID.class)) {
            return method.invoke(target, inventory, player.getUniqueId());
        }
        return false;
    }

    private boolean isAllowedByHolder(Player player, Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return false;
        }
        String holderName = holder.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (!holderName.contains("virtual") && !holderName.contains("storage")) {
            return false;
        }

        for (String methodName : new String[]{"getOwner", "owner", "getOwnerUniqueId", "getOwnerId", "getUniqueId"}) {
            try {
                Method method = holder.getClass().getMethod(methodName);
                Object owner = method.invoke(holder);
                if (matchesOwner(player, owner)) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    private boolean matchesOwner(Player player, Object owner) {
        if (owner instanceof UUID uuid) {
            return uuid.equals(player.getUniqueId());
        }
        if (owner instanceof Player ownerPlayer) {
            return ownerPlayer.getUniqueId().equals(player.getUniqueId());
        }
        if (owner instanceof String ownerString) {
            return ownerString.equalsIgnoreCase(player.getUniqueId().toString())
                    || ownerString.equalsIgnoreCase(player.getName());
        }
        return false;
    }

    private void debug(String message) {
        if (debug) {
            logger.info(message);
        }
    }
}
