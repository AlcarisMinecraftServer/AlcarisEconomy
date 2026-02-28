package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CommandUtils {

    private CommandUtils() {}

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
