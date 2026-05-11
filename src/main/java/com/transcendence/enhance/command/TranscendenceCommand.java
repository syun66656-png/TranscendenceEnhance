package com.transcendence.enhance.command;

import com.transcendence.enhance.gui.TranscendenceGuiListener;
import com.transcendence.enhance.item.EnhancementMaterialFactory;
import com.transcendence.enhance.item.EnhancementMaterialType;
import com.transcendence.enhance.service.EnhancementType;
import com.transcendence.enhance.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TranscendenceCommand implements CommandExecutor, TabCompleter {

    private final TranscendenceGuiListener guiListener;
    private final EnhancementMaterialFactory materialFactory;

    public TranscendenceCommand(TranscendenceGuiListener guiListener, EnhancementMaterialFactory materialFactory) {
        this.guiListener = guiListener;
        this.materialFactory = materialFactory;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            return handleGiveCommand(sender, args);
        }

        EnhancementType type = EnhancementType.TRANSCENDENCE;
        if (args.length > 0) {
            type = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "efficiency" -> EnhancementType.EFFICIENCY;
                case "fortune" -> EnhancementType.FORTUNE;
                default -> null;
            };
            if (type == null) {
                sender.sendMessage(TextUtil.color("&c사용법: /transcendence [efficiency|fortune]"));
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.color("&c플레이어만 GUI를 열 수 있습니다."));
            return true;
        }
        if (!player.hasPermission("transcendence.use")) {
            player.sendMessage(TextUtil.color("&c권한이 없습니다."));
            return true;
        }

        guiListener.openGui(player, type);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterByPrefix(List.of("efficiency", "fortune", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filterByPrefix(List.of("stone", "enhance"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && isMaterialArgument(args[1])) {
            return filterByPrefix(onlinePlayerNames(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && isMaterialArgument(args[1])) {
            return filterByPrefix(List.of("1", "8", "16", "32", "64"), args[3]);
        }
        return Collections.emptyList();
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("transcendence.admin")) {
            sender.sendMessage(TextUtil.color("&c관리 권한이 없습니다."));
            return true;
        }

        if (args.length >= 3 && isMaterialArgument(args[1])) {
            EnhancementMaterialType type = parseMaterialType(args[1]);
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(TextUtil.color("&c대상 플레이어를 찾을 수 없습니다."));
                return true;
            }
            int amount = parseAmount(sender, args.length >= 4 ? args[3] : "1");
            if (amount < 1) {
                return true;
            }
            giveMaterial(sender, target, type, amount);
            return true;
        }

        return handleLegacyGiveCommand(sender, args);
    }

    private boolean handleLegacyGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendGiveUsage(sender);
            return true;
        }

        Player target;
        String amountArg;
        if (isInteger(args[1])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtil.color("&c콘솔은 대상을 지정해야 합니다."));
                return true;
            }
            target = player;
            amountArg = args[1];
        } else {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sendGiveUsage(sender);
                return true;
            }
            amountArg = args.length >= 3 ? args[2] : "1";
        }

        int amount = parseAmount(sender, amountArg);
        if (amount < 1) {
            return true;
        }
        giveMaterial(sender, target, EnhancementMaterialType.TRANSCENDENCE_STONE, amount);
        return true;
    }

    private void giveMaterial(CommandSender sender, Player target, EnhancementMaterialType type, int amount) {
        target.getInventory().addItem(materialFactory.create(type, amount)).values()
                .forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage(TextUtil.color("&a" + type.displayName() + " " + amount + "개를 지급했습니다: " + target.getName()));
    }

    private void sendGiveUsage(CommandSender sender) {
        sender.sendMessage(TextUtil.color("&7사용법: /transcendence give stone <플레이어> [수량]"));
        sender.sendMessage(TextUtil.color("&7사용법: /transcendence give enhance <플레이어> [수량]"));
    }

    private int parseAmount(CommandSender sender, String input) {
        try {
            return Math.max(1, Integer.parseInt(input));
        } catch (NumberFormatException ex) {
            sender.sendMessage(TextUtil.color("&c수량은 숫자여야 합니다."));
            return -1;
        }
    }

    private boolean isMaterialArgument(String input) {
        return parseMaterialType(input) != null;
    }

    private EnhancementMaterialType parseMaterialType(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "stone", "transcendence" -> EnhancementMaterialType.TRANSCENDENCE_STONE;
            case "enhance", "enhancement" -> EnhancementMaterialType.ENHANCEMENT_STONE;
            default -> null;
        };
    }

    private boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private List<String> filterByPrefix(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
