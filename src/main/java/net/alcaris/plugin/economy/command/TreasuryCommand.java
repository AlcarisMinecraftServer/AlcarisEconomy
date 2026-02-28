package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.bank.TreasuryManager;
import net.alcaris.plugin.economy.bank.TreasuryRepository;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.config.MessageConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class TreasuryCommand implements CommandExecutor, TabCompleter {

    private final AlcarisEconomy plugin;
    private final TreasuryManager treasuryManager;
    private final EconomyConfig config;
    private final Logger logger;

    public TreasuryCommand(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.treasuryManager = plugin.getTreasuryManager();
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.treasury")) return true;
        if (args.length == 0) return showHelp(sender);
        return switch (args[0].toLowerCase()) {
            case "list"     -> cmdList(sender);
            case "show"     -> cmdShow(sender, args);
            case "deposit"  -> cmdDeposit(sender, args);
            case "withdraw" -> cmdWithdraw(sender, args);
            case "log"      -> cmdLog(sender, args);
            case "transfer" -> cmdTransfer(sender, args);
            default -> showHelp(sender);
        };
    }

    private boolean cmdList(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<TreasuryRepository.TreasuryRow> rows = treasuryManager.listAll();
                CommandUtils.msg(sender, "&8&m----&r &6&l国庫一覧 &8&m----");
                for (TreasuryRepository.TreasuryRow row : rows) {
                    CommandUtils.msg(sender, String.format("&e%-15s &a%s &7(%s)",
                            row.key(), config.format(row.balance()),
                            row.description() != null ? row.description() : ""));
                }
            } catch (SQLException e) { logger.warning("[TreasuryCommand] list failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdShow(CommandSender sender, String[] args) {
        if (args.length < 2) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/treasury show <key>")); return true; }
        String key = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!treasuryManager.exists(key)) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TREASURY_NOT_FOUND, "key", key)); return;
                }
                long balance = treasuryManager.getBalance(key);
                CommandUtils.msg(sender, "&8&m----&r &6国庫: &e" + key + " &8&m----");
                CommandUtils.msg(sender, " &7残高: &a" + config.format(balance));
            } catch (SQLException e) { logger.warning("[TreasuryCommand] show failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdDeposit(CommandSender sender, String[] args) {
        if (args.length < 3) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/treasury deposit <key> <amount>")); return true; }
        String key = args[1];
        double amount = CommandUtils.parsePositiveDouble(args[2]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);
        UUID actorUuid = sender instanceof Player p ? p.getUniqueId() : null;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!treasuryManager.exists(key)) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TREASURY_NOT_FOUND, "key", key)); return;
                }
                treasuryManager.adminDeposit(key, internal, actorUuid);
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TREASURY_DEPOSITED,
                        "amount", config.format(internal), "key", key));
            } catch (SQLException e) { logger.warning("[TreasuryCommand] deposit failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdWithdraw(CommandSender sender, String[] args) {
        if (args.length < 4) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/treasury withdraw <key> <amount> <player>")); return true; }
        String key = args[1];
        double amount = CommandUtils.parsePositiveDouble(args[2]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[3]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[3])); return true; }
        UUID actorUuid = sender instanceof Player p ? p.getUniqueId() : null;
        String targetName = CommandUtils.displayName(target);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                treasuryManager.withdraw(key, internal, target.getUniqueId(), actorUuid);
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TREASURY_WITHDRAWN,
                        "amount", config.format(internal), "key", key, "player", targetName));
            } catch (SQLException e) {
                if (e.getMessage().contains("Insufficient")) {
                    CommandUtils.msg(sender, MessageConfig.TREASURY_INSUFFICIENT);
                } else {
                    logger.warning("[TreasuryCommand] withdraw failed: " + e.getMessage());
                }
            }
        });
        return true;
    }

    private boolean cmdLog(CommandSender sender, String[] args) {
        if (args.length < 2) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/treasury log <key> [page]")); return true; }
        String key = args[1];
        int page = args.length >= 3 ? Math.max(1, parseInt(args[2])) : 1;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<TreasuryRepository.TreasuryLogRow> rows = treasuryManager.getLog(key, page);
                CommandUtils.msg(sender, "&8&m----&r &6国庫ログ: &e" + key + " &7(ページ " + page + ") &8&m----");
                if (rows.isEmpty()) { CommandUtils.msg(sender, "&7エントリがありません。"); return; }
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
                for (TreasuryRepository.TreasuryLogRow row : rows) {
                    String sign = row.amount() >= 0 ? "&a+" : "&c";
                    CommandUtils.msg(sender, "&7" + sdf.format(new Date(row.createdAt())) +
                            " " + sign + config.format(Math.abs(row.amount())) +
                            " &8[" + row.type() + "]" +
                            (row.note() != null ? " &7" + row.note() : ""));
                }
            } catch (SQLException e) { logger.warning("[TreasuryCommand] log failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdTransfer(CommandSender sender, String[] args) {
        if (args.length < 4) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/treasury transfer <from> <to> <amount>")); return true; }
        String fromKey = args[1];
        String toKey = args[2];
        double amount = CommandUtils.parsePositiveDouble(args[3]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                treasuryManager.transfer(fromKey, toKey, internal);
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TREASURY_TRANSFERRED,
                        "amount", config.format(internal), "from", fromKey, "to", toKey));
            } catch (SQLException e) {
                if (e.getMessage().contains("Insufficient")) {
                    CommandUtils.msg(sender, MessageConfig.TREASURY_INSUFFICIENT);
                } else {
                    logger.warning("[TreasuryCommand] transfer failed: " + e.getMessage());
                }
            }
        });
        return true;
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6&l国庫コマンド &8&m----");
        CommandUtils.msg(sender, "&e/treasury list &7- 全国庫の残高を表示");
        CommandUtils.msg(sender, "&e/treasury show <キー> &7- 国庫の詳細を表示");
        CommandUtils.msg(sender, "&e/treasury deposit <キー> <金額> &7- 管理者入金");
        CommandUtils.msg(sender, "&e/treasury withdraw <キー> <金額> <プレイヤー> &7- プレイヤーへ出金");
        CommandUtils.msg(sender, "&e/treasury log <キー> [ページ] &7- 取引ログを表示");
        CommandUtils.msg(sender, "&e/treasury transfer <送り元> <送り先> <金額> &7- 国庫間移動");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("list", "show", "deposit", "withdraw", "log", "transfer");
        return List.of();
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 1; }
    }
}
