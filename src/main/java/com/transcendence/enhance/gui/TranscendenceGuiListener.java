package com.transcendence.enhance.gui;

import com.transcendence.enhance.TranscendenceEnhancePlugin;
import com.transcendence.enhance.service.EnhancementResult;
import com.transcendence.enhance.service.EnhancementService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class TranscendenceGuiListener implements Listener {

    private static final int GUI_SIZE = 27;
    private static final int TOTAL_PROCESS_TICKS = 100; // fixed 5 seconds
    private static final int BEAT_INTERVAL_TICKS = 20;  // one beat every second

    private final TranscendenceEnhancePlugin plugin;
    private final EnhancementService enhancementService;
    private final NamespacedKey stoneKey;
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final Map<UUID, List<BukkitTask>> scheduledTasksByPlayer = new HashMap<>();
    private final Map<UUID, ProcessingContext> processingContexts = new HashMap<>();

    private enum GuiState {
        WAIT_TOOL,
        INVALID_TOOL,
        WAIT_STONE,
        INVALID_STONE,
        RESULT_NOT_CLAIMED,
        READY
    }

    private static final class ProcessingContext {
        private final ItemStack originalTool;
        private final ItemStack originalStone;

        private ProcessingContext(ItemStack originalTool, ItemStack originalStone) {
            this.originalTool = originalTool;
            this.originalStone = originalStone;
        }
    }

    public TranscendenceGuiListener(TranscendenceEnhancePlugin plugin, EnhancementService enhancementService, NamespacedKey stoneKey) {
        this.plugin = plugin;
        this.enhancementService = enhancementService;
        this.stoneKey = stoneKey;
    }

    public void openGui(Player player) {
        Inventory inventory = Bukkit.createInventory(new TranscendenceInventoryHolder(), GUI_SIZE,
                color(getConfig().getString("Settings.GUI_Title", "&8[ 초월 강화 시스템 ]")));
        fillBackground(inventory);
        refreshState(inventory);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!isOurInventory(top)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        int startSlot = slot("Slots.Start_Button", 22);
        int resultSlot = slot("Slots.Result_Output", 16);

        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        if (processingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot < 0 || rawSlot >= GUI_SIZE) {
            return;
        }

        if (rawSlot == startSlot) {
            event.setCancelled(true);
            attemptEnhancement(player, top);
            return;
        }

        if (isBackgroundSlot(rawSlot, toolSlot, materialSlot, startSlot, resultSlot)) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot == resultSlot) {
            // result slot is output only: allow take-only with empty cursor.
            ItemStack current = top.getItem(resultSlot);
            if (current == null || current.getType() == Material.AIR) {
                event.setCancelled(true);
                return;
            }
            if (event.getHotbarButton() >= 0) {
                event.setCancelled(true);
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                event.setCancelled(true);
                return;
            }
            switch (event.getAction()) {
                case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> {
                    Bukkit.getScheduler().runTask(plugin, () -> refreshState(top));
                    return;
                }
                default -> {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (rawSlot == toolSlot || rawSlot == materialSlot) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshState(top));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isOurInventory(event.getView().getTopInventory())) {
            return;
        }
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);

        for (int raw : event.getRawSlots()) {
            if (raw >= GUI_SIZE) {
                continue;
            }
            if (raw != toolSlot && raw != materialSlot) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshState(event.getView().getTopInventory()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (!isOurInventory(inventory)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        cancelAllTasks(uuid);
        processingPlayers.remove(uuid);

        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        int resultSlot = slot("Slots.Result_Output", 16);

        ProcessingContext context = processingContexts.remove(uuid);
        if (context != null) {
            // Enhancement canceled mid-process: return currently visible input items.
            giveBackIfPresent(player, inventory.getItem(toolSlot));
            giveBackIfPresent(player, inventory.getItem(materialSlot));
        } else {
            giveBackIfPresent(player, inventory.getItem(toolSlot));
            giveBackIfPresent(player, inventory.getItem(materialSlot));
            giveBackIfPresent(player, inventory.getItem(resultSlot));
        }

        inventory.setItem(toolSlot, null);
        inventory.setItem(materialSlot, null);
        inventory.setItem(resultSlot, null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleDisconnectRefund(event.getPlayer());
    }

    private void attemptEnhancement(Player player, Inventory inventory) {
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        ItemStack tool = inventory.getItem(toolSlot);
        ItemStack stone = inventory.getItem(materialSlot);

        GuiState state = evaluateState(inventory, tool, stone);
        if (state != GuiState.READY) {
            refreshState(inventory);
            return;
        }

        int cost = enhancementService.getCost(tool);
        if (!hasEnoughCost(player, cost)) {
            player.sendMessage(color("&c강화 비용이 부족합니다."));
            return;
        }

        UUID uuid = player.getUniqueId();
        processingPlayers.add(uuid);
        processingContexts.put(uuid, new ProcessingContext(tool.clone(), stone.clone()));
        cancelAllTasks(uuid);
        updateStartButton(inventory, "Items.Start_Button_Running", false, cost, color("&e진행중..."));

        runProgressBeats(player, uuid);

        BukkitTask finalTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                finalizeEnhancement(player, inventory, uuid, cost);
            } finally {
                processingPlayers.remove(uuid);
                cancelAllTasks(uuid);
                refreshState(inventory);
            }
        }, TOTAL_PROCESS_TICKS);
        trackTask(uuid, finalTask);
    }

    private void finalizeEnhancement(Player player, Inventory inventory, UUID uuid, int cost) {
        if (!isOurInventory(inventory)) {
            return;
        }

        ProcessingContext context = processingContexts.remove(uuid);
        if (context == null) {
            return;
        }

        int resultSlot = slot("Slots.Result_Output", 16);
        ItemStack existingResult = inventory.getItem(resultSlot);
        if (existingResult != null && existingResult.getType() != Material.AIR) {
            giveBackIfPresent(player, existingResult);
        }

        EnhancementResult result = enhancementService.enhance(context.originalTool);
        if (!result.isValid()) {
            giveBackIfPresent(player, context.originalTool);
            giveBackIfPresent(player, context.originalStone);
            return;
        }

        if (result.isSuccess()) {
            clearInputSlots(inventory);
            inventory.setItem(resultSlot, result.getUpgradedItem());
            playSuccess(player);
            broadcastSuccess(player);
            updateStartButton(inventory, "Items.Start_Button_Disabled", false, cost, color("&e결과물을 먼저 회수하세요"));
            return;
        }

        // fail: keep tool intact, consume only stone
        clearInputSlots(inventory);
        inventory.setItem(resultSlot, context.originalTool);
        playFail(player);
        playFailBreak(player);
        String failMsg = color(getConfig().getString("Settings.Message_Fail", "&c[초월] 강화에 실패했습니다."));
        if (!failMsg.isBlank()) {
            player.sendMessage(failMsg);
        }
        updateStartButton(inventory, "Items.Start_Button_Disabled", false, cost, color("&e결과물을 먼저 회수하세요"));
    }

    private void runProgressBeats(Player player, UUID uuid) {
        Sound hammer = safeSound(getConfig().getString("Settings.Sound_Hammer", "BLOCK_ANVIL_USE"), Sound.BLOCK_ANVIL_USE);
        Particle progressParticle = safeParticle(getConfig().getString("Settings.Effect_Progress", "ENCHANTMENT_TABLE"), Particle.ENCHANTMENT_TABLE);

        for (int beat = 0; beat < 5; beat++) {
            BukkitTask beatTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!processingPlayers.contains(uuid)) {
                    return;
                }
                player.playSound(player.getLocation(), hammer, 0.75f, 0.95f);
                player.spawnParticle(progressParticle, player.getLocation().add(0, 1.0, 0), 10, 0.25, 0.2, 0.25, 0.01);
            }, (long) beat * BEAT_INTERVAL_TICKS);
            trackTask(uuid, beatTask);
        }
    }

    private void trackTask(UUID uuid, BukkitTask task) {
        scheduledTasksByPlayer.computeIfAbsent(uuid, k -> new ArrayList<>()).add(task);
    }

    private void cancelAllTasks(UUID uuid) {
        List<BukkitTask> tasks = scheduledTasksByPlayer.remove(uuid);
        if (tasks == null) {
            return;
        }
        for (BukkitTask task : tasks) {
            if (task != null) {
                task.cancel();
            }
        }
    }

    public void shutdownAndRefundAll() {
        // Called during plugin disable to prevent stuck processing contexts.
        Set<UUID> uuids = new HashSet<>(processingContexts.keySet());
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                handleDisconnectRefund(player);
            } else {
                cancelAllTasks(uuid);
                processingPlayers.remove(uuid);
                processingContexts.remove(uuid);
            }
        }
    }

    private void handleDisconnectRefund(Player player) {
        UUID uuid = player.getUniqueId();
        ProcessingContext context = processingContexts.remove(uuid);
        cancelAllTasks(uuid);
        processingPlayers.remove(uuid);
        if (context == null) {
            return;
        }

        // Prevent duplicate refunds when quit/close event order varies.
        Inventory top = player.getOpenInventory().getTopInventory();
        if (isOurInventory(top)) {
            int toolSlot = slot("Slots.Tool_Input", 10);
            int materialSlot = slot("Slots.Material_Input", 12);
            int resultSlot = slot("Slots.Result_Output", 16);
            giveBackIfPresent(player, top.getItem(toolSlot));
            giveBackIfPresent(player, top.getItem(materialSlot));
            top.setItem(toolSlot, null);
            top.setItem(materialSlot, null);
            top.setItem(resultSlot, null);
            return;
        }

        giveBackIfPresent(player, context.originalTool);
        giveBackIfPresent(player, context.originalStone);
    }

    private void playSuccess(Player player) {
        Sound sound = safeSound(getConfig().getString("Settings.Sound_Success", "UI_TOAST_CHALLENGE_COMPLETE"), Sound.UI_TOAST_CHALLENGE_COMPLETE);
        Particle particle = safeParticle(getConfig().getString("Settings.Effect_Success", "VILLAGER_HAPPY"), Particle.VILLAGER_HAPPY);
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        player.spawnParticle(particle, player.getLocation().add(0, 1.0, 0), 50, 0.6, 0.4, 0.6, 0.01);
    }

    private void playFail(Player player) {
        Sound sound = safeSound(getConfig().getString("Settings.Sound_Fail", "BLOCK_ANVIL_LAND"), Sound.BLOCK_ANVIL_LAND);
        Particle particle = safeParticle(getConfig().getString("Settings.Effect_Fail", "SMOKE_NORMAL"), Particle.SMOKE_NORMAL);
        player.playSound(player.getLocation(), sound, 1.0f, 0.8f);
        player.spawnParticle(particle, player.getLocation().add(0, 1.0, 0), 28, 0.5, 0.3, 0.5, 0.01);
    }

    private void playFailBreak(Player player) {
        Sound sound = safeSound(getConfig().getString("Settings.Sound_Fail_Break", "ENTITY_ITEM_BREAK"), Sound.ENTITY_ITEM_BREAK);
        player.playSound(player.getLocation(), sound, 0.9f, 1.0f);
    }

    private void broadcastSuccess(Player player) {
        String msg = getConfig().getString("Settings.Broadcast_Success", "");
        if (msg == null || msg.isBlank()) {
            return;
        }
        Bukkit.broadcastMessage(color(msg).replace("{player}", player.getName()));
    }

    private boolean hasEnoughCost(Player player, int cost) {
        // Vault/economy integration point: currently always true because cost is 0.
        return true;
    }

    private boolean isValidEnhanceTool(ItemStack tool) {
        if (!enhancementService.isEnhanceableTool(tool)) {
            return false;
        }

        ItemMeta meta = tool.getItemMeta();
        if (meta == null) {
            return false;
        }

        int requiredModelData = getConfig().getInt("Items.Enhanceable_Tool_Filter.CustomModelData", -1);
        if (requiredModelData >= 0 && (!meta.hasCustomModelData() || meta.getCustomModelData() != requiredModelData)) {
            return false;
        }

        String requiredEquals = normalizeName(getConfig().getString("Items.Enhanceable_Tool_Filter.DisplayName_Equals", ""));
        if (!requiredEquals.isEmpty()) {
            if (!meta.hasDisplayName()) {
                return false;
            }
            return normalizeName(meta.getDisplayName()).equals(requiredEquals);
        }

        return true;
    }

    private String normalizeName(String input) {
        if (input == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(color(input));
        return stripped == null ? "" : stripped.trim();
    }

    private boolean isTranscendenceStone(ItemStack material) {
        if (material == null || material.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = material.getItemMeta();
        if (meta == null) {
            return false;
        }

        int modelData = getConfig().getInt("Items.Transcendence_Stone.CustomModelData", 10001);
        String expectedName = color(getConfig().getString("Items.Transcendence_Stone.Name", "&b&l[초월석]"));
        boolean pdcMarked = meta.getPersistentDataContainer().has(stoneKey, PersistentDataType.BYTE);
        boolean modelMatch = meta.hasCustomModelData() && meta.getCustomModelData() == modelData;
        boolean nameMatch = meta.hasDisplayName() && meta.getDisplayName().equals(expectedName);
        return (pdcMarked || modelMatch) && nameMatch;
    }

    private GuiState evaluateState(Inventory inventory, ItemStack tool, ItemStack stone) {
        int resultSlot = slot("Slots.Result_Output", 16);
        ItemStack result = inventory.getItem(resultSlot);
        if (result != null && result.getType() != Material.AIR) {
            return GuiState.RESULT_NOT_CLAIMED;
        }

        if (tool == null || tool.getType() == Material.AIR) {
            return GuiState.WAIT_TOOL;
        }
        if (tool.getAmount() != 1 || !isValidEnhanceTool(tool)) {
            return GuiState.INVALID_TOOL;
        }
        if (stone == null || stone.getType() == Material.AIR) {
            return GuiState.WAIT_STONE;
        }
        if (stone.getAmount() != 1 || !isTranscendenceStone(stone)) {
            return GuiState.INVALID_STONE;
        }
        return GuiState.READY;
    }

    private void refreshState(Inventory inventory) {
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);

        if (processingPlayers.contains(viewerUuid(inventory))) {
            updateStartButton(inventory, "Items.Start_Button_Running", false, 0, color("&e강화 진행중"));
            return;
        }

        ItemStack tool = inventory.getItem(toolSlot);
        ItemStack stone = inventory.getItem(materialSlot);
        GuiState state = evaluateState(inventory, tool, stone);
        int cost = (tool != null && tool.getType() != Material.AIR) ? enhancementService.getCost(tool) : 0;

        switch (state) {
            case READY -> {
                updateStartButton(inventory, "Items.Start_Button", true, cost, color("&a강화 가능"));
            }
            case WAIT_TOOL -> {
                updateStartButton(inventory, "Items.Start_Button_Disabled", false, cost, color("&c도구 필요"));
            }
            case INVALID_TOOL -> {
                updateStartButton(inventory, "Items.Start_Button_Disabled", false, cost, color("&c도구 불일치"));
            }
            case WAIT_STONE -> {
                updateStartButton(inventory, "Items.Start_Button_Disabled", false, cost, color("&c초월석 필요"));
            }
            case INVALID_STONE -> {
                updateStartButton(inventory, "Items.Start_Button_Disabled", false, cost, color("&c초월석 불일치"));
            }
            case RESULT_NOT_CLAIMED -> {
                updateStartButton(inventory, "Items.Start_Button_Disabled", false, cost, color("&e결과 회수 필요"));
            }
        }
    }

    private UUID viewerUuid(Inventory inventory) {
        if (inventory.getViewers().isEmpty()) {
            return new UUID(0L, 0L);
        }
        return inventory.getViewers().get(0).getUniqueId();
    }

    private void updateStartButton(Inventory inventory, String sectionPath, boolean enabled, int cost, String subtitle) {
        int startSlot = slot("Slots.Start_Button", 22);
        ItemStack button = createConfiguredItem(sectionPath);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add(enabled ? color("&f비용: &a" + cost) : color("&f비용: &7" + cost));
            lore.add(subtitle);
            meta.setLore(lore);
            button.setItemMeta(meta);
        }
        inventory.setItem(startSlot, button);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack background = createConfiguredItem("Items.Background");
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        int startSlot = slot("Slots.Start_Button", 22);
        int resultSlot = slot("Slots.Result_Output", 16);

        for (int i = 0; i < inventory.getSize(); i++) {
            if (!isBackgroundSlot(i, toolSlot, materialSlot, startSlot, resultSlot)) {
                continue;
            }
            inventory.setItem(i, background.clone());
        }
    }

    private boolean isBackgroundSlot(int slot, int toolSlot, int materialSlot, int startSlot, int resultSlot) {
        return slot != toolSlot && slot != materialSlot && slot != startSlot && slot != resultSlot;
    }

    private void clearInputSlots(Inventory inventory) {
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        inventory.setItem(toolSlot, null);
        inventory.setItem(materialSlot, null);
    }

    private ItemStack createConfiguredItem(String sectionPath) {
        ConfigurationSection section = getConfig().getConfigurationSection(sectionPath);
        Material material = Material.GRAY_STAINED_GLASS_PANE;
        String name = " ";
        List<String> lore = Collections.emptyList();
        int modelData = -1;

        if (section != null) {
            material = safeMaterial(section.getString("Material"), material);
            name = color(section.getString("Name", name));
            lore = color(section.getStringList("Lore"));
            modelData = section.getInt("CustomModelData", -1);
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            if (modelData >= 0) {
                meta.setCustomModelData(modelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void giveBackIfPresent(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private boolean isOurInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof TranscendenceInventoryHolder && inventory.getSize() == GUI_SIZE;
    }

    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    private int slot(String path, int fallback) {
        return getConfig().getInt(path, fallback);
    }

    private String color(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "§");
    }

    private List<String> color(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
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

    private Sound safeSound(String input, Sound fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private Particle safeParticle(String input, Particle fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return Particle.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
