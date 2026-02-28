package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.bank.TransferManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.config.MessageConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import net.alcaris.plugin.economy.util.BalanceProviderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class MoneyCommand implements CommandExecutor, TabCompleter {

    private static final int TOP_PAGE_SIZE = 10;

    private final AlcarisEconomy plugin;
    private final BalanceRepository repository;
    private final TransferManager transferManager;
    private final EconomyConfig config;
    private final Logger logger;

    public MoneyCommand(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.repository = plugin.getRepository();
        this.transferManager = plugin.getTransferManager();
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("pay")) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "pay";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            return onCommand(sender, command, "money", newArgs);
        }

        if (args.length == 0) return cmdShow(sender, args);

        return switch (args[0].toLowerCase()) {
            case "show"   -> cmdShow(sender, args);
            case "pay"    -> cmdPay(sender, args);
            case "set"    -> cmdSet(sender, args);
            case "give"   -> cmdGive(sender, args);
            case "take"   -> cmdTake(sender, args);
            case "create" -> cmdCreate(sender, args);
            case "remove" -> cmdRemove(sender, args);
            case "top"    -> cmdTop(sender, args);
            case "reload" -> cmdReload(sender, args);
            case "help"   -> showHelp(sender);
            default       -> showHelp(sender);
        };
    }

    private boolean cmdShow(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (CommandUtils.checkPermission(sender, "alcariseconomy.money.others")) return true;
            OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
            if (target == null) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1]));
                return true;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (!repository.hasAccount(target.getUniqueId())) {
                        CommandUtils.msg(sender, MessageConfig.format(
                                MessageConfig.TARGET_NO_ACCOUNT, "player", CommandUtils.displayName(target)));
                        return;
                    }
                    long balance = repository.getBalance(target.getUniqueId());
                    CommandUtils.msg(sender, MessageConfig.format(
                            MessageConfig.BALANCE_OTHER,
                            "player", CommandUtils.displayName(target),
                            "balance", config.format(balance)));
                } catch (SQLException e) {
                    logger.warning("[MoneyCommand] show failed: " + e.getMessage());
                }
            });
        } else {
            Player player = CommandUtils.requirePlayer(sender);
            if (player == null) return true;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (!repository.hasAccount(player.getUniqueId())) {
                        CommandUtils.msg(sender, MessageConfig.NO_ACCOUNT);
                        return;
                    }
                    List<String> lines = BalanceProviderRegistry.buildLines(player).join();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        CommandUtils.msg(sender, "&8&m----&r &6残高情報 &8&m----");
                        lines.forEach(line -> CommandUtils.msg(sender, line));
                    });
                } catch (SQLException e) {
                    logger.warning("[MoneyCommand] show-self failed: " + e.getMessage());
                }
            });
        }
        return true;
    }

    private boolean cmdPay(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 3) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/money pay <player> <amount>"));
            return true;
        }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null || target.getName() == null) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1]));
            return true;
        }
        double amount = CommandUtils.parsePositiveDouble(args[2]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);
        UUID fromUUID = player.getUniqueId();
        UUID toUUID = target.getUniqueId();
        String targetName = CommandUtils.displayName(target);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!repository.hasAccount(fromUUID)) { CommandUtils.msg(sender, MessageConfig.NO_ACCOUNT); return; }
                if (!repository.hasAccount(toUUID)) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TARGET_NO_ACCOUNT, "player", targetName));
                    return;
                }
            } catch (SQLException e) {
                logger.warning("[MoneyCommand] pay pre-check failed: " + e.getMessage());
                return;
            }

            TransferManager.TransferResult result = transferManager.transfer(
                    fromUUID, toUUID, internal, TransferManager.TransferType.REMOTE_PAY);

            if (result.success()) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PAY_SENT,
                        "amount", config.format(result.amount()),
                        "player", targetName,
                        "fee", config.format(result.fee())));
                Player onlineTarget = Bukkit.getPlayer(toUUID);
                if (onlineTarget != null) {
                    CommandUtils.msg(onlineTarget, MessageConfig.format(
                            MessageConfig.PAY_RECEIVED,
                            "player", player.getName(),
                            "amount", config.format(result.amount())));
                }
            } else {
                handleTransferFailure(sender, result.failReason(), internal);
            }
        });
        return true;
    }

    private boolean cmdSet(CommandSender sender, String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.money.set")) return true;
        if (args.length < 3) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/money set <player> <amount>"));
            return true;
        }
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
            } catch (SQLException e) { logger.warning("[MoneyCommand] set failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdGive(CommandSender sender, String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.money.give")) return true;
        if (args.length < 3) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/money give <player> <amount>"));
            return true;
        }
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
            } catch (SQLException e) { logger.warning("[MoneyCommand] give failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdTake(CommandSender sender, String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.money.take")) return true;
        if (args.length < 3) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/money take <player> <amount>"));
            return true;
        }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        double amount = CommandUtils.parsePositiveDouble(args[2]);
        if (amount < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long internal = CommandUtils.toInternal(amount);
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long balance = repository.getBalance(target.getUniqueId());
                long newBal = Math.max(0, balance - internal);
                repository.setBalance(target.getUniqueId(), newBal);
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.BALANCE_TAKEN, "player", targetName, "amount", config.format(internal)));
            } catch (SQLException e) { logger.warning("[MoneyCommand] take failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdCreate(CommandSender sender, String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.money.create")) return true;
        if (args.length < 2) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/money create <player>"));
            return true;
        }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (repository.hasAccount(target.getUniqueId())) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_EXISTS, "player", targetName));
                    return;
                }
                repository.createAccount(target.getUniqueId(), config.getDefaultBalance());
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_CREATED, "player", targetName));
            } catch (SQLException e) { logger.warning("[MoneyCommand] create failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdRemove(CommandSender sender, String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.money.remove")) return true;
        if (args.length < 2) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/money remove <player>"));
            return true;
        }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        String targetName = CommandUtils.displayName(target);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.deleteAccount(target.getUniqueId());
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_REMOVED, "player", targetName));
            } catch (SQLException e) { logger.warning("[MoneyCommand] remove failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdTop(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Integer.parseInt(args[1])); }
            catch (NumberFormatException ignored) {}
        }
        final int finalPage = page;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Map.Entry<UUID, Long>> top = repository.getTopBalances(TOP_PAGE_SIZE * 10);
                if (top.isEmpty()) { CommandUtils.msg(sender, MessageConfig.TOP_EMPTY); return; }
                int maxPage = (int) Math.ceil((double) top.size() / TOP_PAGE_SIZE);
                int clampedPage = Math.min(finalPage, maxPage);
                int start = (clampedPage - 1) * TOP_PAGE_SIZE;
                int end = Math.min(start + TOP_PAGE_SIZE, top.size());

                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TOP_HEADER,
                        "page", String.valueOf(clampedPage), "max", String.valueOf(maxPage)));

                for (int i = start; i < end; i++) {
                    Map.Entry<UUID, Long> entry = top.get(i);
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = op.getName() != null ? op.getName() : entry.getKey().toString();
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.TOP_ENTRY,
                            "rank", String.valueOf(i + 1),
                            "player", name,
                            "balance", config.format(entry.getValue())));
                }
            } catch (SQLException e) {
                logger.warning("[MoneyCommand] top failed: " + e.getMessage());
            }
        });
        return true;
    }

    private boolean cmdReload(CommandSender sender, String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.money.reload")) return true;
        plugin.reloadConfig();
        CommandUtils.msg(sender, MessageConfig.RELOADED);
        return true;
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6&lAlcarisEconomy &8&m----");
        CommandUtils.msg(sender, "&e/money show [プレイヤー] &7- 残高を表示");
        CommandUtils.msg(sender, "&e/money pay <プレイヤー> <金額> &7- 送金");
        CommandUtils.msg(sender, "&e/money top [ページ] &7- 残高ランキング");
        if (sender.hasPermission("alcariseconomy.money.set")) {
            CommandUtils.msg(sender, "&e/money set/give/take <プレイヤー> <金額> &7- [管理者] 残高を操作");
            CommandUtils.msg(sender, "&e/money create/remove <プレイヤー> &7- [管理者] アカウント管理");
            CommandUtils.msg(sender, "&e/money reload &7- [管理者] 設定をリロード");
        }
        CommandUtils.msg(sender, "&e/bank &7- 銀行・ATM操作");
        CommandUtils.msg(sender, "&e/crypto &7- 仮想通貨");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, String alias, @NotNull String[] args) {
        if (alias.equalsIgnoreCase("pay")) {
            if (args.length == 1) return null;
            return List.of();
        }
        if (args.length == 1) {
            return Arrays.asList("show", "pay", "set", "give", "take", "create", "remove", "top", "reload", "help");
        }
        if (args.length == 2 && Arrays.asList("pay", "set", "give", "take", "create", "remove", "show").contains(args[0].toLowerCase())) {
            return null;
        }
        return List.of();
    }

    private void handleTransferFailure(CommandSender sender, String reason, long amount) {
        if (reason == null) { CommandUtils.msg(sender, "&c送金に失敗しました。"); return; }
        if (reason.equals("SENDER_FROZEN")) { CommandUtils.msg(sender, MessageConfig.SENDER_FROZEN); }
        else if (reason.startsWith("RECEIVER_FROZEN")) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.RECEIVER_FROZEN, "player", "相手")); }
        else if (reason.startsWith("INSUFFICIENT")) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.INSUFFICIENT_FUNDS,
                    "amount", config.format(amount), "balance", "?"));
        }
        else { CommandUtils.msg(sender, "&c送金に失敗しました: " + reason); }
    }
}
