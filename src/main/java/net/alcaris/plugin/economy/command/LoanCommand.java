package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.loan.PlayerLoanService;
import net.alcaris.plugin.economy.loan.ServerLoanRepository;
import net.alcaris.plugin.economy.loan.ServerLoanService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class LoanCommand implements CommandExecutor, TabCompleter {

    private final AlcarisEconomy plugin;
    private final PlayerLoanService playerLoanService;
    private final ServerLoanService serverLoanService;
    private final EconomyConfig config;
    private final Logger logger;

    public LoanCommand(AlcarisEconomy plugin, PlayerLoanService playerLoanService,
                       ServerLoanService serverLoanService) {
        this.plugin = plugin;
        this.playerLoanService = playerLoanService;
        this.serverLoanService = serverLoanService;
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.loan")) return true;
        if (args.length == 0) return showHelp(sender);

        return switch (args[0].toLowerCase()) {
            case "offer"   -> cmdOffer(sender, args);
            case "accept"  -> cmdAccept(sender, args);
            case "deny"    -> cmdDeny(sender, args);
            case "cancel"  -> cmdCancel(sender, args);
            case "repay"   -> cmdRepay(sender, args);
            case "borrow"  -> cmdBorrow(sender, args);
            case "status"  -> cmdStatus(sender);
            case "autopay" -> cmdAutopay(sender, args);
            default        -> showHelp(sender);
        };
    }

    private boolean cmdOffer(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 5) {
            CommandUtils.msg(sender, "&c使い方: /loan offer <相手> <貸付額> <返済額> <日数>");
            return true;
        }
        OfflinePlayer borrower = CommandUtils.findOfflinePlayer(args[1]);
        if (borrower == null) { CommandUtils.msg(sender, "&cプレイヤーが見つかりません。"); return true; }
        double principal = CommandUtils.parsePositiveDouble(args[2]);
        double repayAmount = CommandUtils.parsePositiveDouble(args[3]);
        if (principal < 0 || repayAmount < 0) { CommandUtils.msg(sender, "&c無効な金額です。"); return true; }
        int durationDays;
        try { durationDays = Integer.parseInt(args[4]); } catch (NumberFormatException e) {
            CommandUtils.msg(sender, "&c日数が無効です。"); return true;
        }
        long principalInternal = CommandUtils.toInternal(principal);
        long repayInternal = CommandUtils.toInternal(repayAmount);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long loanId = playerLoanService.create(player, borrower, principalInternal, repayInternal, durationDays);
                CommandUtils.msg(sender, "&a[ローン] 申請を送信しました。ローンID: &f" + loanId);
            } catch (IllegalArgumentException e) {
                CommandUtils.msg(sender, "&cローン申請に失敗しました: " + e.getMessage());
            } catch (IllegalStateException e) {
                String msg = e.getMessage();
                if ("LENDER_FROZEN".equals(msg)) CommandUtils.msg(sender, "&cアカウントが凍結されています。");
                else if ("INSUFFICIENT".equals(msg)) CommandUtils.msg(sender, "&c残高が不足しています。");
                else CommandUtils.msg(sender, "&cエラー: " + msg);
            } catch (SQLException e) {
                logger.warning("[LoanCommand] offer failed: " + e.getMessage());
                CommandUtils.msg(sender, "&cDBエラーが発生しました。");
            }
        });
        return true;
    }

    private boolean cmdAccept(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 2) { CommandUtils.msg(sender, "&c使い方: /loan accept <ローンID>"); return true; }
        long loanId = parseLoanId(sender, args[1]);
        if (loanId < 0) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String err = playerLoanService.accept(player, loanId);
                if (err == null) CommandUtils.msg(sender, "&a[ローン] ローンを承諾しました。");
                else handlePlayerLoanError(sender, err);
            } catch (SQLException e) {
                logger.warning("[LoanCommand] accept failed: " + e.getMessage());
                CommandUtils.msg(sender, "&cDBエラーが発生しました。");
            }
        });
        return true;
    }

    private boolean cmdDeny(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 2) { CommandUtils.msg(sender, "&c使い方: /loan deny <ローンID>"); return true; }
        long loanId = parseLoanId(sender, args[1]);
        if (loanId < 0) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String err = playerLoanService.deny(player, loanId);
                if (err == null) CommandUtils.msg(sender, "&a[ローン] ローンを拒否しました。");
                else handlePlayerLoanError(sender, err);
            } catch (SQLException e) {
                logger.warning("[LoanCommand] deny failed: " + e.getMessage());
                CommandUtils.msg(sender, "&cDBエラーが発生しました。");
            }
        });
        return true;
    }

    private boolean cmdCancel(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 2) { CommandUtils.msg(sender, "&c使い方: /loan cancel <ローンID>"); return true; }
        long loanId = parseLoanId(sender, args[1]);
        if (loanId < 0) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String err = playerLoanService.cancel(player, loanId);
                if (err == null) CommandUtils.msg(sender, "&a[ローン] 申請をキャンセルしました。");
                else handlePlayerLoanError(sender, err);
            } catch (SQLException e) {
                logger.warning("[LoanCommand] cancel failed: " + e.getMessage());
                CommandUtils.msg(sender, "&cDBエラーが発生しました。");
            }
        });
        return true;
    }

    private boolean cmdRepay(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 2) { CommandUtils.msg(sender, "&c使い方: /loan repay <金額> または /loan repay <ローンID> <金額>"); return true; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (args.length >= 3) {
                    long loanId = parseLoanId(sender, args[1]);
                    if (loanId < 0) return;
                    double amount = CommandUtils.parsePositiveDouble(args[2]);
                    if (amount < 0) { CommandUtils.msg(sender, "&c無効な金額です。"); return; }
                    String err = playerLoanService.repay(player, loanId, CommandUtils.toInternal(amount));
                    if (err == null) CommandUtils.msg(sender, "&a[ローン] 返済しました。");
                    else handlePlayerLoanError(sender, err);
                } else {
                    double amount = CommandUtils.parsePositiveDouble(args[1]);
                    if (amount < 0) { CommandUtils.msg(sender, "&c無効な金額です。"); return; }
                    String err = serverLoanService.repay(player, CommandUtils.toInternal(amount));
                    if (err == null) CommandUtils.msg(sender, "&a[サーバーローン] 返済しました。");
                    else handleServerLoanError(sender, err);
                }
            } catch (SQLException e) {
                logger.warning("[LoanCommand] repay failed: " + e.getMessage());
                CommandUtils.msg(sender, "&cDBエラーが発生しました。");
            }
        });
        return true;
    }

    private boolean cmdBorrow(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 2) { CommandUtils.msg(sender, "&c使い方: /loan borrow <金額>"); return true; }
        double amount = CommandUtils.parsePositiveDouble(args[1]);
        if (amount < 0) { CommandUtils.msg(sender, "&c無効な金額です。"); return true; }
        long internal = CommandUtils.toInternal(amount);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String err = serverLoanService.borrow(player, internal);
                if (err == null) CommandUtils.msg(sender, "&a[サーバーローン] " + config.format(internal) + " を借入しました。");
                else handleServerLoanError(sender, err);
            } catch (SQLException e) {
                logger.warning("[LoanCommand] borrow failed: " + e.getMessage());
                CommandUtils.msg(sender, "&cDBエラーが発生しました。");
            }
        });
        return true;
    }

    private boolean cmdStatus(CommandSender sender) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ServerLoanRepository.ServerLoanRow row = serverLoanService.getRow(player);
                if (row == null || (row.principal() <= 0 && row.interestDebt() <= 0)) {
                    CommandUtils.msg(sender, "&7[サーバーローン] 現在借入はありません。");
                } else {
                    CommandUtils.msg(sender, "&8&m----&r &6サーバーローン状況 &8&m----");
                    CommandUtils.msg(sender, " &7元本: &f" + config.format(row.principal()));
                    CommandUtils.msg(sender, " &7利息未払: &f" + config.format(row.interestDebt()));
                    CommandUtils.msg(sender, " &7ステージ: &f" + row.stage());
                    CommandUtils.msg(sender, " &7延滞日数: &f" + row.overdueDays() + "日");
                    if (row.autopayAmount() != null) {
                        CommandUtils.msg(sender, " &7自動返済: &f" + config.format(row.autopayAmount()) + "/日");
                    }
                }
            } catch (SQLException e) {
                logger.warning("[LoanCommand] status failed: " + e.getMessage());
            }
        });
        return true;
    }

    private boolean cmdAutopay(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        Long autopay = null;
        if (args.length >= 2 && !args[1].equalsIgnoreCase("off")) {
            double amount = CommandUtils.parsePositiveDouble(args[1]);
            if (amount < 0) { CommandUtils.msg(sender, "&c無効な金額です。"); return true; }
            autopay = CommandUtils.toInternal(amount);
        }
        final Long finalAutopay = autopay;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String err = serverLoanService.setAutopay(player, finalAutopay);
                if (err == null) {
                    if (finalAutopay == null) CommandUtils.msg(sender, "&a自動返済を無効にしました。");
                    else CommandUtils.msg(sender, "&a自動返済を " + config.format(finalAutopay) + "/日 に設定しました。");
                } else {
                    handleServerLoanError(sender, err);
                }
            } catch (SQLException e) {
                logger.warning("[LoanCommand] autopay failed: " + e.getMessage());
            }
        });
        return true;
    }

    private long parseLoanId(CommandSender sender, String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            CommandUtils.msg(sender, "&cローンIDが無効です。");
            return -1;
        }
    }

    private void handlePlayerLoanError(CommandSender sender, String err) {
        switch (err) {
            case "EXPIRED"       -> CommandUtils.msg(sender, "&cローン申請の有効期限が切れました。");
            case "NOT_FOUND"     -> CommandUtils.msg(sender, "&cローンが見つかりません。");
            case "NOT_PENDING"   -> CommandUtils.msg(sender, "&cこのローンは承認待ち状態ではありません。");
            case "NOT_ACTIVE"    -> CommandUtils.msg(sender, "&cこのローンはアクティブではありません。");
            case "INSUFFICIENT"  -> CommandUtils.msg(sender, "&c残高が不足しています。");
            case "LENDER_INSUFFICIENT" -> CommandUtils.msg(sender, "&c貸し手の残高が不足しています。");
            default              -> CommandUtils.msg(sender, "&cエラー: " + err);
        }
    }

    private void handleServerLoanError(CommandSender sender, String err) {
        switch (err) {
            case "DISABLED"       -> CommandUtils.msg(sender, "&cサーバーローン機能は無効です。");
            case "FROZEN"         -> CommandUtils.msg(sender, "&cアカウントが凍結されています。");
            case "ALREADY_HAS_LOAN" -> CommandUtils.msg(sender, "&cすでにサーバーローンがあります。返済してからお申込みください。");
            case "EXCEEDS_LIMIT"  -> CommandUtils.msg(sender, "&c借入限度額を超えています。");
            case "INVALID_AMOUNT" -> CommandUtils.msg(sender, "&c無効な金額です。");
            case "NO_LOAN"        -> CommandUtils.msg(sender, "&c現在サーバーローンはありません。");
            case "INSUFFICIENT"   -> CommandUtils.msg(sender, "&c残高が不足しています。");
            default               -> CommandUtils.msg(sender, "&cエラー: " + err);
        }
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6ローンコマンド &8&m----");
        CommandUtils.msg(sender, "&e/loan offer <相手> <貸付額> <返済額> <日数> &7- P2Pローンを申請");
        CommandUtils.msg(sender, "&e/loan accept <ID> &7- ローンを承諾");
        CommandUtils.msg(sender, "&e/loan deny <ID> &7- ローンを拒否");
        CommandUtils.msg(sender, "&e/loan cancel <ID> &7- ローン申請をキャンセル");
        CommandUtils.msg(sender, "&e/loan repay <ローンID> <金額> &7- P2Pローンを返済");
        CommandUtils.msg(sender, "&e/loan borrow <金額> &7- サーバーからローン");
        CommandUtils.msg(sender, "&e/loan repay <金額> &7- サーバーローンを返済");
        CommandUtils.msg(sender, "&e/loan status &7- サーバーローン状況を確認");
        CommandUtils.msg(sender, "&e/loan autopay <金額|off> &7- 自動返済を設定");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("alcariseconomy.loan")) return List.of();
        String partial = args[args.length - 1];

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (playerLoanService != null) subs.addAll(Arrays.asList("offer", "accept", "deny", "cancel"));
            if (playerLoanService != null || serverLoanService != null) subs.add("repay");
            if (serverLoanService != null) subs.addAll(Arrays.asList("borrow", "status", "autopay"));
            return CommandUtils.filter(subs, partial);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("offer")) return filterOnlinePlayers(partial);
            if (Arrays.asList("accept", "deny", "cancel", "repay").contains(sub))
                return List.of();
            if (sub.equals("autopay")) return CommandUtils.filter(List.of("off"), partial);
        }
        return List.of();
    }

    private static List<String> filterOnlinePlayers(String partial) {
        String lower = partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .collect(java.util.stream.Collectors.toList());
    }
}
