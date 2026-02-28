package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.config.MessageConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AlcarisEconomy plugin;
    private final BalanceRepository repository;
    private final FreezeManager freezeManager;
    private final Logger logger;

    public AdminCommand(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.repository = plugin.getRepository();
        this.freezeManager = plugin.getFreezeManager();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.admin")) return true;
        return dispatch(sender, args);
    }

    public boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) return showHelp(sender);
        return switch (args[0].toLowerCase()) {
            case "freeze"   -> cmdFreeze(sender, args);
            case "unfreeze" -> cmdUnfreeze(sender, args);
            case "check"    -> cmdCheck(sender, args);
            case "pool"     -> cmdPool(sender);
            default -> showHelp(sender);
        };
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return Arrays.asList("freeze", "unfreeze", "check", "pool");
        if (args.length == 2 && !args[0].equalsIgnoreCase("pool")) return null;
        return List.of();
    }

    private boolean cmdFreeze(CommandSender sender, String[] args) {
        if (args.length < 2) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/economy admin freeze <player>")); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = freezeManager.freeze(target.getUniqueId());
            if (ok) CommandUtils.msg(sender, MessageConfig.format(MessageConfig.FREEZE_ADMIN, "player", targetName));
            else    CommandUtils.msg(sender, "&c" + targetName + " の凍結に失敗しました。");
        });
        return true;
    }

    private boolean cmdUnfreeze(CommandSender sender, String[] args) {
        if (args.length < 2) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/economy admin unfreeze <player>")); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String err = freezeManager.unfreeze(target.getUniqueId(), false);
            if (err == null) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.UNFREEZE_ADMIN, "player", targetName));
            } else if (err.equals("NOT_FROZEN")) {
                CommandUtils.msg(sender, "&e" + targetName + " &eは凍結されていません。");
            } else {
                CommandUtils.msg(sender, "&c凍結解除に失敗しました: " + err);
            }
        });
        return true;
    }

    private boolean cmdCheck(CommandSender sender, String[] args) {
        if (args.length < 2) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/economy admin check <player>")); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!repository.hasAccount(target.getUniqueId())) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TARGET_NO_ACCOUNT, "player", targetName)); return;
                }
                long balance = repository.getBalance(target.getUniqueId());
                boolean frozen = repository.isFrozen(target.getUniqueId());
                long lastTxn = repository.getLastTxnAt(target.getUniqueId());
                String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastTxn));

                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_HEADER, "player", targetName));
                CommandUtils.msg(sender, " &7UUID: &f" + target.getUniqueId());
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_BALANCE,
                        "balance", plugin.getEconomyConfig().format(balance)));
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_FROZEN,
                        "frozen", frozen ? "&cはい" : "&aいいえ"));
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_LAST_TXN, "time", timeStr));
            } catch (SQLException e) { logger.warning("[AdminCommand] check failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdPool(CommandSender sender) {
        String stats = plugin.getDbManager().getPoolStats();
        CommandUtils.msg(sender, "&8[DB Pool] &f" + stats);
        return true;
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6&l管理者コマンド &8&m----");
        CommandUtils.msg(sender, "&e/economy admin freeze <player> &7- アカウントを凍結");
        CommandUtils.msg(sender, "&e/economy admin unfreeze <player> &7- アカウント凍結を解除");
        CommandUtils.msg(sender, "&e/economy admin check <player> &7- アカウント詳細を表示");
        CommandUtils.msg(sender, "&e/economy admin pool &7- DBプール統計を表示");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return tabComplete(sender, args);
    }
}
