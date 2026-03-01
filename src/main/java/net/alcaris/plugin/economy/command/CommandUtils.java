package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandUtils {

    private CommandUtils() {}

    public static List<String> filter(List<String> options, String partial) {
        if (partial == null || partial.isEmpty()) return new ArrayList<>(options);
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    public static String formatTransferType(String type) {
        if (type == null) return "不明";
        return switch (type) {
            case "REMOTE_PAY"        -> "送金";
            case "ATM_TRANSFER"      -> "ATM振込";
            case "INTEREST"          -> "利息";
            case "FREEZE_FEE"        -> "凍結解除";
            case "CRYPTO"            -> "仮想通貨";
            case "TREASURY_WITHDRAW" -> "国庫出金";
            case "CHEQUE_ISSUE"      -> "小切手発行";
            case "CHEQUE_USE"        -> "小切手換金";
            case "LOAN_BORROW"       -> "ローン借入";
            case "LOAN_REPAY"        -> "ローン返済";
            case "LOAN_INTEREST"     -> "ローン利息";
            default -> type;
        };
    }

    public static String formatLoanStage(String stage) {
        if (stage == null) return "不明";
        return switch (stage) {
            case "NORMAL"    -> "&a通常";
            case "OVERDUE_1" -> "&e延滞（段階1 - 口座凍結）";
            case "OVERDUE_2" -> "&c延滞（段階2 - 高利率）";
            default -> stage;
        };
    }

    public static double parsePositiveDouble(String s) {
        try {
            double v = Double.parseDouble(s);
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static long toInternal(double yen) {
        return Math.round(yen * EconomyConfig.MULTIPLIER);
    }

    public static OfflinePlayer findOfflinePlayer(String name) {
        if (name == null || name.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayer(name);
    }

    public static void msg(CommandSender sender, String legacyMessage) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyMessage);
        sender.sendMessage(component);
    }

    public static boolean checkPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return false;
        msg(sender, net.alcaris.plugin.economy.config.MessageConfig.NO_PERMISSION);
        return true;
    }

    public static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        msg(sender, net.alcaris.plugin.economy.config.MessageConfig.PLAYER_ONLY);
        return null;
    }

    public static String fmt(long internal, EconomyConfig cfg) {
        return cfg.format(internal);
    }

    public static String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : player.getUniqueId().toString();
    }
}
