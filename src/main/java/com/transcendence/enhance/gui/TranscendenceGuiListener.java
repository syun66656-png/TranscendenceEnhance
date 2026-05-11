package com.transcendence.enhance.gui;

import com.transcendence.enhance.TranscendenceEnhancePlugin;
import com.transcendence.enhance.item.EnhancementMaterialFactory;
import com.transcendence.enhance.service.EnhancementPlan;
import com.transcendence.enhance.service.EnhancementResult;
import com.transcendence.enhance.service.EnhancementService;
import com.transcendence.enhance.service.EnhancementType;
import com.transcendence.enhance.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TranscendenceGuiListener implements Listener {

    private static final int GUI_SIZE = 27;
    private static final int TOTAL_PROCESS_TICKS = 100;
    private static final int BEAT_INTERVAL_TICKS = 20;

    private final TranscendenceEnhancePlugin plugin;
    private final EnhancementService enhancementService;
    private final EnhancementMaterialFactory materialFactory;
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final Map<UUID, List<BukkitTask>> scheduledTasksByPlayer = new HashMap<>();
    private final Map<UUID, ProcessingContext> processingContexts = new HashMap<>();

    private enum GuiState {
        WAIT_TOOL,
        INVALID_TOOL,
        MAX_LEVEL,
        WAIT_MATERIAL,
        INVALID_MATERIAL,
        NOT_ENOUGH_MATERIAL,
        RESULT_NOT_CLAIMED,
        READY
    }

    private record Evaluation(GuiState state, EnhancementPlan plan) {
    }

    private record ProcessingContext(EnhancementType type, EnhancementPlan plan, ItemStack originalTool, ItemStack originalMaterial) {
    }

    public TranscendenceGuiListener(
            TranscendenceEnhancePlugin plugin,
            EnhancementService enhancementService,
            EnhancementMaterialFactory materialFactory
    ) {
        this.plugin = plugin;
        this.enhancementService = enhancementService;
        this.materialFactory = materialFactory;
    }

    public void openGui(Player player, EnhancementType type) {
        String fallbackTitle = switch (type) {
            case TRANSCENDENCE -> "&8[ 초월 강화 시스템 ]";
            case EFFICIENCY -> "&8[ 효율 강화 시스템 ]";
            case FORTUNE -> "&8[ 행운 강화 시스템 ]";
        };
        Inventory inventory = Bukkit.createInventory(
                new TranscendenceInventoryHolder(type),
                GUI_SIZE,
                TextUtil.color(getConfig().getString(type.titlePath(), fallbackTitle))
        );
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
        int resultSlot = slot("Slots.Result_Output", 16);
        int startSlot = slot("Slots.Start_Button", 22);

        if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        UUID uuid = player.getUniqueId();
        if (processingPlayers.contains(uuid)) {
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
            handleResultSlotClick(event, top, resultSlot);
            return;
        }

        if (rawSlot == toolSlot || rawSlot == materialSlot) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshState(top));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isOurInventory(top)) {
            return;
        }

        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= GUI_SIZE) {
                continue;
            }
            if (rawSlot != toolSlot && rawSlot != materialSlot) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshState(top));
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
        ProcessingContext context = processingContexts.remove(uuid);

        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        int resultSlot = slot("Slots.Result_Output", 16);

        if (context != null) {
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

    public void shutdownAndRefundAll() {
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

    private void handleResultSlotClick(InventoryClickEvent event, Inventory top, int resultSlot) {
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
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> Bukkit.getScheduler().runTask(plugin, () -> refreshState(top));
            default -> event.setCancelled(true);
        }
    }

    private void attemptEnhancement(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof TranscendenceInventoryHolder holder)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (processingPlayers.contains(uuid)) {
            return;
        }

        Evaluation evaluation = evaluateState(holder.type(), inventory);
        if (evaluation.state() != GuiState.READY || evaluation.plan() == null) {
            refreshState(inventory);
            sendBlockedMessage(player, evaluation.state());
            return;
        }

        EnhancementPlan plan = evaluation.plan();
        if (!hasEnoughCost(player, plan.cost())) {
            player.sendMessage(TextUtil.color(getConfig().getString("Messages.Invalid_Input", "&c[강화] 강화 조건이 충족되지 않았습니다.")));
            return;
        }

        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        ItemStack tool = inventory.getItem(toolSlot);
        ItemStack material = inventory.getItem(materialSlot);
        if (tool == null || material == null) {
            refreshState(inventory);
            return;
        }

        processingPlayers.add(uuid);
        processingContexts.put(uuid, new ProcessingContext(holder.type(), plan, tool.clone(), material.clone()));
        cancelAllTasks(uuid);
        updateStartButton(inventory, "Items.Start_Button_Running", false, plan, "&e강화 진행중");

        runProgressBeats(player, uuid);
        BukkitTask finalTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                finalizeEnhancement(player, inventory, uuid);
            } finally {
                processingPlayers.remove(uuid);
                cancelAllTasks(uuid);
                refreshState(inventory);
            }
        }, TOTAL_PROCESS_TICKS);
        trackTask(uuid, finalTask);
    }

    private void finalizeEnhancement(Player player, Inventory inventory, UUID uuid) {
        ProcessingContext context = processingContexts.remove(uuid);
        if (context == null || !isOurInventory(inventory)) {
            return;
        }

        int resultSlot = slot("Slots.Result_Output", 16);
        ItemStack existingResult = inventory.getItem(resultSlot);
        if (existingResult != null && existingResult.getType() != Material.AIR) {
            giveBackIfPresent(player, existingResult);
        }

        EnhancementResult result = enhancementService.enhance(context.plan(), context.originalTool());
        if (!result.isValid() || result.getResultItem() == null) {
            giveBackIfPresent(player, context.originalTool());
            giveBackIfPresent(player, context.originalMaterial());
            clearInputSlots(inventory);
            return;
        }

        clearInputSlots(inventory);
        giveBackRemainingMaterial(player, context.originalMaterial(), context.plan().stoneAmount());
        inventory.setItem(resultSlot, result.getResultItem());

        if (result.isSuccess()) {
            playSuccess(player);
            sendSuccessMessage(player, context.type());
        } else {
            playFail(player);
            playFailBreak(player);
            sendConfiguredMessage(player, "Messages.Fail", "&c[강화] 강화에 실패했습니다.");
        }

        updateStartButton(inventory, "Items.Start_Button_Disabled", false, context.plan(), "&e결과 회수 필요");
    }

    private Evaluation evaluateState(EnhancementType type, Inventory inventory) {
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        int resultSlot = slot("Slots.Result_Output", 16);

        ItemStack result = inventory.getItem(resultSlot);
        if (result != null && result.getType() != Material.AIR) {
            return new Evaluation(GuiState.RESULT_NOT_CLAIMED, null);
        }

        ItemStack tool = inventory.getItem(toolSlot);
        if (tool == null || tool.getType() == Material.AIR) {
            return new Evaluation(GuiState.WAIT_TOOL, null);
        }

        Optional<EnhancementPlan> plan = enhancementService.createPlan(type, tool);
        if (plan.isEmpty()) {
            GuiState state = enhancementService.isAtMaximum(type, tool) ? GuiState.MAX_LEVEL : GuiState.INVALID_TOOL;
            return new Evaluation(state, null);
        }

        ItemStack material = inventory.getItem(materialSlot);
        if (material == null || material.getType() == Material.AIR) {
            return new Evaluation(GuiState.WAIT_MATERIAL, plan.get());
        }
        if (!materialFactory.isMaterial(type.materialType(), material)) {
            return new Evaluation(GuiState.INVALID_MATERIAL, plan.get());
        }
        if (material.getAmount() < plan.get().stoneAmount()) {
            return new Evaluation(GuiState.NOT_ENOUGH_MATERIAL, plan.get());
        }

        return new Evaluation(GuiState.READY, plan.get());
    }

    private void refreshState(Inventory inventory) {
        if (!(inventory.getHolder() instanceof TranscendenceInventoryHolder holder)) {
            return;
        }

        if (processingPlayers.contains(viewerUuid(inventory))) {
            updateStartButton(inventory, "Items.Start_Button_Running", false, null, "&e강화 진행중");
            return;
        }

        Evaluation evaluation = evaluateState(holder.type(), inventory);
        String sectionPath = evaluation.state() == GuiState.READY ? "Items.Start_Button" : "Items.Start_Button_Disabled";
        updateStartButton(inventory, sectionPath, evaluation.state() == GuiState.READY, evaluation.plan(), stateMessage(evaluation.state(), holder.type()));
    }

    private void updateStartButton(Inventory inventory, String sectionPath, boolean enabled, EnhancementPlan plan, String subtitle) {
        int startSlot = slot("Slots.Start_Button", 22);
        ItemStack button = createConfiguredItem(sectionPath);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            if (plan != null) {
                appendPlanLore(lore, plan);
                lore.add(Component.empty());
            }
            lore.add(TextUtil.color(enabled ? "&a강화 가능" : subtitle));
            meta.lore(lore);
            button.setItemMeta(meta);
        }
        inventory.setItem(startSlot, button);
    }

    private void appendPlanLore(List<Component> lore, EnhancementPlan plan) {
        lore.add(TextUtil.color("&f[ " + plan.type().displayName() + " &f]"));
        if (plan.type() == EnhancementType.TRANSCENDENCE) {
            lore.add(TextUtil.color(plan.fromDisplayName() + " &f→ " + plan.toDisplayName()));
        } else {
            String enchantName = plan.type() == EnhancementType.EFFICIENCY ? "효율" : "행운";
            lore.add(TextUtil.color("&f" + enchantName + " " + plan.currentLevel() + " &f→ " + enchantName + " " + plan.nextLevel()));
        }
        lore.add(TextUtil.color("&f필요 " + plan.type().materialType().displayName() + ": &a" + plan.stoneAmount() + "개"));
        lore.add(TextUtil.color("&f강화 비용: &a" + plan.cost()));
        lore.add(TextUtil.color("&f성공 확률: &a" + plan.successRate() + "%"));
    }

    private void fillBackground(Inventory inventory) {
        ItemStack background = createConfiguredItem("Items.Background");
        int toolSlot = slot("Slots.Tool_Input", 10);
        int materialSlot = slot("Slots.Material_Input", 12);
        int resultSlot = slot("Slots.Result_Output", 16);
        int startSlot = slot("Slots.Start_Button", 22);

        for (int i = 0; i < inventory.getSize(); i++) {
            if (isBackgroundSlot(i, toolSlot, materialSlot, startSlot, resultSlot)) {
                inventory.setItem(i, background.clone());
            }
        }
    }

    private ItemStack createConfiguredItem(String sectionPath) {
        ConfigurationSection section = getConfig().getConfigurationSection(sectionPath);
        Material material = Material.GRAY_STAINED_GLASS_PANE;
        String name = " ";
        List<String> lore = Collections.emptyList();
        int modelData = -1;

        if (section != null) {
            material = safeMaterial(section.getString("Material"), material);
            name = section.getString("Name", name);
            lore = section.getStringList("Lore");
            modelData = section.getInt("CustomModelData", -1);
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.color(name));
            if (!lore.isEmpty()) {
                meta.lore(TextUtil.colorList(lore));
            }
            if (modelData >= 0) {
                meta.setCustomModelData(modelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void runProgressBeats(Player player, UUID uuid) {
        Sound hammer = safeSound(getConfig().getString("Sound.Hammer", "BLOCK_ANVIL_USE"), "BLOCK_ANVIL_USE");
        Particle progress = safeParticle(getConfig().getString("Particle.Progress", "ENCHANTMENT_TABLE"), "ENCHANTMENT_TABLE", "ENCHANT");

        for (int beat = 0; beat < 5; beat++) {
            BukkitTask beatTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!processingPlayers.contains(uuid)) {
                    return;
                }
                if (hammer != null) {
                    player.playSound(player.getLocation(), hammer, 0.75f, 0.95f);
                }
                if (progress != null) {
                    player.spawnParticle(progress, player.getLocation().add(0, 1.0, 0), 10, 0.25, 0.2, 0.25, 0.01);
                }
            }, (long) beat * BEAT_INTERVAL_TICKS);
            trackTask(uuid, beatTask);
        }
    }

    private void playSuccess(Player player) {
        Sound sound = safeSound(getConfig().getString("Sound.Success", "UI_TOAST_CHALLENGE_COMPLETE"), "UI_TOAST_CHALLENGE_COMPLETE");
        Particle particle = safeParticle(getConfig().getString("Particle.Success", "VILLAGER_HAPPY"), "VILLAGER_HAPPY", "HAPPY_VILLAGER");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
        if (particle != null) {
            player.spawnParticle(particle, player.getLocation().add(0, 1.0, 0), 50, 0.6, 0.4, 0.6, 0.01);
        }
    }

    private void playFail(Player player) {
        Sound sound = safeSound(getConfig().getString("Sound.Fail", "BLOCK_ANVIL_LAND"), "BLOCK_ANVIL_LAND");
        Particle particle = safeParticle(getConfig().getString("Particle.Fail", "SMOKE_NORMAL"), "SMOKE_NORMAL", "SMOKE");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 0.8f);
        }
        if (particle != null) {
            player.spawnParticle(particle, player.getLocation().add(0, 1.0, 0), 28, 0.5, 0.3, 0.5, 0.01);
        }
    }

    private void playFailBreak(Player player) {
        Sound sound = safeSound(getConfig().getString("Sound.Fail_Break", "ENTITY_ITEM_BREAK"), "ENTITY_ITEM_BREAK");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 0.9f, 1.0f);
        }
    }

    private void sendSuccessMessage(Player player, EnhancementType type) {
        switch (type) {
            case TRANSCENDENCE -> {
                String message = getConfig().getString("Messages.Transcendence_Success_Broadcast", "");
                if (message != null && !message.isBlank()) {
                    Bukkit.broadcast(TextUtil.color(message.replace("{player}", player.getName())));
                }
            }
            case EFFICIENCY -> sendConfiguredMessage(player, "Messages.Efficiency_Success", "&a[효율] 강화에 성공했습니다!");
            case FORTUNE -> sendConfiguredMessage(player, "Messages.Fortune_Success", "&a[행운] 강화에 성공했습니다!");
        }
    }

    private void sendBlockedMessage(Player player, GuiState state) {
        switch (state) {
            case MAX_LEVEL -> sendConfiguredMessage(player, "Messages.Max_Level", "&c[강화] 이미 최대 단계입니다.");
            case NOT_ENOUGH_MATERIAL -> sendConfiguredMessage(player, "Messages.Not_Enough_Material", "&c[강화] 재료 수량이 부족합니다.");
            case INVALID_TOOL, INVALID_MATERIAL -> sendConfiguredMessage(player, "Messages.Invalid_Input", "&c[강화] 강화 조건이 충족되지 않았습니다.");
            default -> {
            }
        }
    }

    private void sendConfiguredMessage(Player player, String path, String fallback) {
        String message = getConfig().getString(path, fallback);
        if (message != null && !message.isBlank()) {
            player.sendMessage(TextUtil.color(message));
        }
    }

    private void giveBackRemainingMaterial(Player player, ItemStack originalMaterial, int consumedAmount) {
        int remaining = originalMaterial.getAmount() - consumedAmount;
        if (remaining <= 0) {
            return;
        }
        ItemStack leftover = originalMaterial.clone();
        leftover.setAmount(remaining);
        giveBackIfPresent(player, leftover);
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

    private void handleDisconnectRefund(Player player) {
        UUID uuid = player.getUniqueId();
        ProcessingContext context = processingContexts.remove(uuid);
        cancelAllTasks(uuid);
        processingPlayers.remove(uuid);
        if (context == null) {
            return;
        }

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

        giveBackIfPresent(player, context.originalTool());
        giveBackIfPresent(player, context.originalMaterial());
    }

    private void clearInputSlots(Inventory inventory) {
        inventory.setItem(slot("Slots.Tool_Input", 10), null);
        inventory.setItem(slot("Slots.Material_Input", 12), null);
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

    private boolean hasEnoughCost(Player player, int cost) {
        return true;
    }

    private String stateMessage(GuiState state, EnhancementType type) {
        return switch (state) {
            case WAIT_TOOL -> "&c도구 필요";
            case INVALID_TOOL -> "&c도구 불일치";
            case MAX_LEVEL -> "&c이미 최대 단계";
            case WAIT_MATERIAL -> "&c" + type.materialType().displayName() + " 필요";
            case INVALID_MATERIAL -> "&c" + type.materialType().displayName() + " 불일치";
            case NOT_ENOUGH_MATERIAL -> "&c재료 수량 부족";
            case RESULT_NOT_CLAIMED -> "&e결과 회수 필요";
            case READY -> "&a강화 가능";
        };
    }

    private UUID viewerUuid(Inventory inventory) {
        if (inventory.getViewers().isEmpty()) {
            return new UUID(0L, 0L);
        }
        return inventory.getViewers().get(0).getUniqueId();
    }

    private boolean isOurInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof TranscendenceInventoryHolder && inventory.getSize() == GUI_SIZE;
    }

    private boolean isBackgroundSlot(int slot, int toolSlot, int materialSlot, int startSlot, int resultSlot) {
        return slot != toolSlot && slot != materialSlot && slot != startSlot && slot != resultSlot;
    }

    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    private int slot(String path, int fallback) {
        return getConfig().getInt(path, fallback);
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

    private Sound safeSound(String input, String fallback) {
        String[] candidates = {input, fallback};
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return Sound.valueOf(candidate.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private Particle safeParticle(String input, String... fallbacks) {
        List<String> candidates = new ArrayList<>();
        candidates.add(input);
        Collections.addAll(candidates, fallbacks);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return Particle.valueOf(candidate.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
