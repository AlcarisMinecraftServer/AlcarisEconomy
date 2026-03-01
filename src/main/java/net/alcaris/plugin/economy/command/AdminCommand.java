package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.config.MessageConfig;
import net.alcaris.plugin.economy.loan.PlayerLoanService;
import net.alcaris.plugin.economy.loan.ServerLoanRepository;
import net.alcaris.plugin.economy.loan.ServerLoanService;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import net.alcaris.plugin.economy.repository.ChequeRepository;
import net.alcaris.plugin.economy.repository.TxLogRepository;
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
    private final EconomyConfig config;
    private final ChequeRepository chequeRepo;
    private final TxLogRepository txLogRepo;
    private final PlayerLoanService playerLoanService;
    private final ServerLoanService serverLoanService;
    private final Logger logger;

    public AdminCommand(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.repository = plugin.getRepository();
        this.freezeManager = plugin.getFreezeManager();
        this.config = plugin.getEconomyConfig();
        this.chequeRepo = plugin.getChequeRepository();
        this.txLogRepo = plugin.getTxLogRepository();
        this.playerLoanService = plugin.getPlayerLoanService();
        this.serverLoanService = plugin.getServerLoanService();
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
            case "set"      -> cmdSet(sender, args);
            case "give"     -> cmdGive(sender, args);
            case "take"     -> cmdTake(sender, args);
            case "cheque"   -> cmdCheque(sender, args);
            case "txlog"    -> cmdTxlog(sender, args);
            case "loan"     -> cmdLoan(sender, args);
            default         -> showHelp(sender);
        };
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alcariseconomy.admin")) return List.of();
        String partial = args[args.length - 1];
        if (args.length == 1)
            return CommandUtils.filter(Arrays.asList("freeze", "unfreeze", "check", "pool",
                    "set", "give", "take", "cheque", "txlog", "loan"), partial);
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("freeze", "unfreeze", "check", "set", "give", "take", "txlog").contains(sub))
                return filterOnlinePlayers(partial);
            if (sub.equals("cheque")) return CommandUtils.filter(Arrays.asList("info", "void"), partial);
            if (sub.equals("loan"))   return CommandUtils.filter(Arrays.asList("info", "forgive", "setstage", "void"), partial);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("loan") && args[1].equalsIgnoreCase("setstage"))
            return CommandUtils.filter(Arrays.asList("NORMAL", "OVERDUE_1", "OVERDUE_2"), partial);
        return List.of();
    }

    private static List<String> filterOnlinePlayers(String partial) {
        String lower = partial.toLowerCase();
        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getName())
                .filter(n -> n.toLowerCase().startsWith(lower))
                .collect(java.util.stream.Collectors.toList());
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
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_BALANCE, "balance", config.format(balance)));
                FreezeManager.FreezeReason reason = frozen ? plugin.getFreezeManager().getPublicFreezeReason(target.getUniqueId()) : null;
                String statusStr = !frozen ? "&a通常"
                        : reason == FreezeManager.FreezeReason.LOAN_OVERDUE ? "&c凍結 &7(ローン延滞)"
                        : "&c凍結 &7(非活性)";
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_FROZEN, "frozen", statusStr));
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_LAST_TXN, "time", timeStr));
            } catch (SQLException e) { logger.warning("[AdminCommand] check failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdPool(CommandSender sender) {
        CommandUtils.msg(sender, "&8[DB Pool] &f" + plugin.getDbManager().getPoolStats());
        return true;
    }

    private boolean cmdSet(CommandSender sender, String[] args) {
        if (args.length < 3) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/economy admin set <player> <amount>")); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        double amount = CommandUtils.parsePositiveDouble(args[2]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.setBalance(target.getUniqueId(), internal);
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.BALANCE_SET, "player", targetName, "amount", config.format(internal)));
            } catch (SQLException e) { logger.warning("[AdminCommand] set failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdGive(CommandSender sender, String[] args) {
        if (args.length < 3) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/economy admin give <player> <amount>")); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        double amount = CommandUtils.parsePositiveDouble(args[2]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.addBalance(target.getUniqueId(), internal);
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.BALANCE_GIVEN, "player", targetName, "amount", config.format(internal)));
            } catch (SQLException e) { logger.warning("[AdminCommand] give failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdTake(CommandSender sender, String[] args) {
        if (args.length < 3) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/economy admin take <player> <amount>")); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        double amount = CommandUtils.parsePositiveDouble(args[2]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long balance = repository.getBalance(target.getUniqueId());
                repository.setBalance(target.getUniqueId(), Math.max(0, balance - internal));
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.BALANCE_TAKEN, "player", targetName, "amount", config.format(internal)));
            } catch (SQLException e) { logger.warning("[AdminCommand] take failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdCheque(CommandSender sender, String[] args) {
        if (chequeRepo == null) { CommandUtils.msg(sender, "&c小切手機能が無効です。"); return true; }
        if (args.length < 3) {
            CommandUtils.msg(sender, "&e/economy admin cheque info <ID>");
            CommandUtils.msg(sender, "&e/economy admin cheque void <ID>");
            return true;
        }
        String sub = args[1].toLowerCase();
        long id;
        try { id = Long.parseLong(args[2]); } catch (NumberFormatException e) {
            CommandUtils.msg(sender, "&cIDが無効です。"); return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ChequeRepository.ChequeRow row = chequeRepo.findById(id);
                if (row == null) { CommandUtils.msg(sender, "&c小切手が見つかりません。"); return; }
                if (sub.equals("info")) {
                    OfflinePlayer issuer = Bukkit.getOfflinePlayer(row.issuerUuid());
                    String issuerName = issuer.getName() != null ? issuer.getName() : row.issuerUuid().toString();
                    CommandUtils.msg(sender, "&8---&r 小切手 #" + id + " &8---");
                    CommandUtils.msg(sender, " &7発行者: &f" + issuerName);
                    CommandUtils.msg(sender, " &7金額: &f" + config.format(row.amount()));
                    CommandUtils.msg(sender, " &7状態: " + (row.used() ? "&c使用済み" : "&a未使用"));
                    if (row.note() != null) CommandUtils.msg(sender, " &7メモ: &f" + row.note());
                } else if (sub.equals("void")) {
                    if (row.used()) { CommandUtils.msg(sender, "&cすでに使用済みです。"); return; }
                    chequeRepo.markVoided(id);
                    CommandUtils.msg(sender, "&a小切手 #" + id + " を無効化しました（返金なし）。");
                } else {
                    CommandUtils.msg(sender, "&c不明なサブコマンドです。");
                }
            } catch (SQLException e) { logger.warning("[AdminCommand] cheque failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdTxlog(CommandSender sender, String[] args) {
        if (txLogRepo == null) { CommandUtils.msg(sender, "&c機能が利用できません。"); return true; }
        if (args.length < 2) { CommandUtils.msg(sender, "&c使い方: /economy admin txlog <player> [page]"); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        int page = 1;
        if (args.length >= 3) { try { page = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) {} }
        final int finalPage = page;
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<TxLogRepository.TxLogEntry> entries = txLogRepo.findByUuid(
                        target.getUniqueId(), 10, (finalPage - 1) * 10);
                if (entries.isEmpty()) { CommandUtils.msg(sender, "&7取引履歴がありません。"); return; }
                CommandUtils.msg(sender, "&8---&r &6" + targetName + " の取引履歴 &7(p" + finalPage + ") &8---");
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
                for (TxLogRepository.TxLogEntry e : entries) {
                    String dir = e.fromUuid() != null && e.fromUuid().equals(target.getUniqueId()) ? "&c-" : "&a+";
                    CommandUtils.msg(sender, "&7" + sdf.format(new Date(e.createdAt())) + " "
                            + dir + config.format(e.amount()) + " &8[" + CommandUtils.formatTransferType(e.type()) + "]");
                }
            } catch (SQLException e) { logger.warning("[AdminCommand] txlog failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdLoan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            CommandUtils.msg(sender, "&e/economy admin loan info <P>");
            CommandUtils.msg(sender, "&e/economy admin loan forgive <P>");
            CommandUtils.msg(sender, "&e/economy admin loan setstage <P> <NORMAL|OVERDUE_1|OVERDUE_2>");
            CommandUtils.msg(sender, "&e/economy admin loan void <ID>");
            return true;
        }
        String sub = args[1].toLowerCase();

        if (sub.equals("void")) {
            if (playerLoanService == null) { CommandUtils.msg(sender, "&cローン機能が無効です。"); return true; }
            if (args.length < 3) { CommandUtils.msg(sender, "&c使い方: /economy admin loan void <ID>"); return true; }
            long loanId;
            try { loanId = Long.parseLong(args[2]); } catch (NumberFormatException e) {
                CommandUtils.msg(sender, "&cIDが無効です。"); return true;
            }
            final long finalId = loanId;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String err = playerLoanService.adminVoid(finalId);
                    if (err == null) CommandUtils.msg(sender, "&aローン #" + finalId + " をキャンセルしました。");
                    else if ("NOT_FOUND".equals(err)) CommandUtils.msg(sender, "&cローンが見つかりません。");
                    else if ("ALREADY_FINAL".equals(err)) CommandUtils.msg(sender, "&cすでに完了/キャンセル済みです。");
                    else CommandUtils.msg(sender, "&cエラー: " + err);
                } catch (SQLException e) { logger.warning("[AdminCommand] loan void failed: " + e.getMessage()); }
            });
            return true;
        }

        if (serverLoanService == null) { CommandUtils.msg(sender, "&cサーバーローン機能が無効です。"); return true; }
        if (args.length < 3) { CommandUtils.msg(sender, "&c対象プレイヤーを指定してください。"); return true; }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[2]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[2])); return true; }
        String targetName = CommandUtils.displayName(target);

        switch (sub) {
            case "info" -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    ServerLoanRepository.ServerLoanRow row = serverLoanService.getRowByUuid(target.getUniqueId());
                    CommandUtils.msg(sender, "&8---&r &6" + targetName + " のローン情報 &8---");
                    if (row == null || (row.principal() <= 0 && row.interestDebt() <= 0)) {
                        CommandUtils.msg(sender, " &7サーバーローン: &aなし");
                    } else {
                        CommandUtils.msg(sender, " &7元本: &f" + config.format(row.principal()));
                        CommandUtils.msg(sender, " &7利息未払: &f" + config.format(row.interestDebt()));
                        CommandUtils.msg(sender, " &7ステージ: " + CommandUtils.formatLoanStage(row.stage()));
                        CommandUtils.msg(sender, " &7延滞日数: &f" + row.overdueDays() + " 日");
                    }
                } catch (SQLException e) { logger.warning("[AdminCommand] loan info failed: " + e.getMessage()); }
            });
            case "forgive" -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String err = serverLoanService.adminForgive(target.getUniqueId());
                    if (err == null) CommandUtils.msg(sender, "&a" + targetName + " のサーバーローンを強制完済しました。");
                    else if ("NO_LOAN".equals(err)) CommandUtils.msg(sender, "&eサーバーローンがありません。");
                    else CommandUtils.msg(sender, "&cエラー: " + err);
                } catch (SQLException e) { logger.warning("[AdminCommand] loan forgive failed: " + e.getMessage()); }
            });
            case "setstage" -> {
                if (args.length < 4) { CommandUtils.msg(sender, "&c使い方: /economy admin loan setstage <P> <NORMAL|OVERDUE_1|OVERDUE_2>"); return true; }
                String stage = args[3].toUpperCase();
                if (!stage.equals("NORMAL") && !stage.equals("OVERDUE_1") && !stage.equals("OVERDUE_2")) {
                    CommandUtils.msg(sender, "&c有効なステージ: NORMAL, OVERDUE_1, OVERDUE_2"); return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        String err = serverLoanService.adminSetStage(target.getUniqueId(), stage);
                        if (err == null) CommandUtils.msg(sender, "&a" + targetName + " のステージを " + stage + " に変更しました。");
                        else if ("NO_LOAN".equals(err)) CommandUtils.msg(sender, "&eサーバーローンがありません。");
                        else CommandUtils.msg(sender, "&cエラー: " + err);
                    } catch (SQLException e) { logger.warning("[AdminCommand] loan setstage failed: " + e.getMessage()); }
                });
            }
            default -> CommandUtils.msg(sender, "&c不明なサブコマンドです。");
        }
        return true;
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6&l管理者コマンド &8&m----");
        CommandUtils.msg(sender, "&e/economy admin freeze/unfreeze/check <player>");
        CommandUtils.msg(sender, "&e/economy admin set/give/take <player> <amount>");
        CommandUtils.msg(sender, "&e/economy admin pool");
        CommandUtils.msg(sender, "&e/economy admin cheque info|void <ID>");
        CommandUtils.msg(sender, "&e/economy admin txlog <player> [page]");
        CommandUtils.msg(sender, "&e/economy admin loan info|forgive|setstage|void <P|ID>");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return tabComplete(sender, args);
    }
}
