package com.transcendence.enhance.command;

import com.transcendence.enhance.gui.TranscendenceGuiListener;
import com.transcendence.enhance.item.TranscendenceStoneFactory;
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
    private final TranscendenceStoneFactory stoneFactory;

    public TranscendenceCommand(TranscendenceGuiListener guiListener, TranscendenceStoneFactory stoneFactory) {
        this.guiListener = guiListener;
        this.stoneFactory = stoneFactory;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            return handleGiveCommand(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 GUI를 열 수 있습니다.");
            return true;
        }
        if (!player.hasPermission("transcendence.use")) {
            player.sendMessage("§c권한이 없습니다.");
            return true;
        }

        guiListener.openGui(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterByPrefix(List.of("give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filterByPrefix(names, args[1]);
        }
        return Collections.emptyList();
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("transcendence.admin")) {
            sender.sendMessage("§c관리 권한이 없습니다.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§7사용법1: /transcendence give <amount>");
            sender.sendMessage("§7사용법2: /transcendence give <player> [amount]");
            return true;
        }

        Player target;
        int amount;

        if (isInteger(args[1])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c콘솔은 대상을 지정해야 합니다.");
                return true;
            }
            target = player;
            try {
                amount = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ex) {
                sender.sendMessage("§c수량은 숫자여야 합니다.");
                return true;
            }
        } else {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§c대상 플레이어를 찾을 수 없습니다.");
                return true;
            }
            amount = 1;
            if (args.length >= 3) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§c수량은 숫자여야 합니다.");
                    return true;
                }
            }
        }

        target.getInventory().addItem(stoneFactory.createStone(amount));
        sender.sendMessage("§a초월석 " + amount + "개를 지급했습니다: " + target.getName());
        return true;
    }

    private boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
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
