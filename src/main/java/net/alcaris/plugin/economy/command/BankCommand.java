package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.bank.TransferManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.config.MessageConfig;
import net.alcaris.plugin.economy.currency.CashItem;
import net.alcaris.plugin.economy.gui.BankMainUI;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class BankCommand implements CommandExecutor, TabCompleter {

    private final AlcarisEconomy plugin;
    private final BalanceRepository repository;
    private final FreezeManager freezeManager;
    private final TransferManager transferManager;
    private final EconomyConfig config;
    private final Logger logger;
    private final Set<Integer> validAmounts;

    public BankCommand(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.repository = plugin.getRepository();
        this.freezeManager = plugin.getFreezeManager();
        this.transferManager = plugin.getTransferManager();
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
        this.validAmounts = CashItem.buildValidAmounts(config.getDenominations());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            Player player = CommandUtils.requirePlayer(sender);
            if (player == null) return true;
            BankMainUI.openAsync(plugin, player);
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "show"     -> cmdShow(sender);
            case "deposit"  -> cmdDeposit(sender);
            case "withdraw" -> cmdWithdraw(sender, args);
            case "transfer" -> cmdTransfer(sender, args);
            case "unfreeze" -> cmdUnfreeze(sender);
            default -> showHelp(sender);
        };
    }

    private boolean cmdShow(CommandSender sender) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!repository.hasAccount(player.getUniqueId())) {
                    CommandUtils.msg(sender, MessageConfig.NO_ACCOUNT); return;
                }
                long balance = repository.getBalance(player.getUniqueId());
                boolean frozen = repository.isFrozen(player.getUniqueId());
                long lastTxn = repository.getLastTxnAt(player.getUniqueId());
                String timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(lastTxn));

                String statusStr;
                if (frozen) {
                    FreezeManager.FreezeReason reason = freezeManager.getPublicFreezeReason(player.getUniqueId());
                    statusStr = reason == FreezeManager.FreezeReason.LOAN_OVERDUE
                            ? "&c凍結 &7(ローン延滞)"
                            : "&c凍結 &7(非活性)";
                } else {
                    statusStr = "&a通常";
                }

                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_HEADER, "player", player.getName()));
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_BALANCE, "balance", config.format(balance)));
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_FROZEN, "frozen", statusStr));
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.ACCOUNT_INFO_LAST_TXN, "time", timeStr));
            } catch (SQLException e) { logger.warning("[BankCommand] show failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdDeposit(CommandSender sender) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (!repository.hasAccount(player.getUniqueId())) {
                    CommandUtils.msg(sender, MessageConfig.NO_ACCOUNT); return;
                }
                if (repository.isFrozen(player.getUniqueId())) {
                    CommandUtils.msg(sender, MessageConfig.SENDER_FROZEN); return;
                }

                long total = 0;
                var inv = player.getInventory();
                String serverKey = config.getServerKey();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item == null) continue;
                    if (CashItem.hasCashMarker(item)) {
                        if (CashItem.isValid(item, serverKey, validAmounts)) {
                            total += (long) CashItem.getAmount(item) * item.getAmount();
                            inv.clear(i);
                        } else {
                            inv.clear(i);
                            plugin.getLogger().warning("[CashItem] Forged item removed from " + player.getName());
                        }
                    }
                }
                if (total == 0) { CommandUtils.msg(sender, MessageConfig.CASH_NO_ITEMS); return; }
                final long finalTotal = total;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        repository.addBalance(player.getUniqueId(), finalTotal);
                        repository.updateLastTxnAt(player.getUniqueId());
                        CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CASH_DEPOSITED,
                                "amount", config.format(finalTotal)));
                    } catch (SQLException e) { logger.warning("[BankCommand] deposit DB failed: " + e.getMessage()); }
                });
            } catch (SQLException e) { logger.warning("[BankCommand] deposit check failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdWithdraw(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 2) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/bank withdraw <amount>"));
            return true;
        }
        long internal = CommandUtils.parsePositiveAmount(args[1]);
        if (internal < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }

        int denomsNeeded = estimateItemCount(internal);
        int freeSlots = countFreeSlots(player);
        if (denomsNeeded > freeSlots) {
            CommandUtils.msg(sender, MessageConfig.INVENTORY_FULL);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!repository.hasAccount(player.getUniqueId())) { CommandUtils.msg(sender, MessageConfig.NO_ACCOUNT); return; }
                if (repository.isFrozen(player.getUniqueId())) { CommandUtils.msg(sender, MessageConfig.SENDER_FROZEN); return; }
                long balance = repository.getBalance(player.getUniqueId());
                if (balance < internal) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.INSUFFICIENT_FUNDS,
                            "amount", config.format(internal), "balance", config.format(balance)));
                    return;
                }
                repository.addBalance(player.getUniqueId(), -internal);
                repository.updateLastTxnAt(player.getUniqueId());

                List<ItemStack> items = CashItem.makeChange(internal, config.getDenominations(), config.getServerKey());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (ItemStack item : items) {
                        player.getInventory().addItem(item).forEach((k, leftover) ->
                                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    }
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CASH_WITHDRAWN, "amount", config.format(internal)));
                });
            } catch (SQLException e) { logger.warning("[BankCommand] withdraw failed: " + e.getMessage()); }
        });
        return true;
    }

    private boolean cmdTransfer(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 3) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/bank transfer <player> <amount>"));
            return true;
        }
        OfflinePlayer target = CommandUtils.findOfflinePlayer(args[1]);
        if (target == null) { CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PLAYER_NOT_FOUND, "player", args[1])); return true; }
        long internal = CommandUtils.parsePositiveAmount(args[2]);
        if (internal < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        String targetName = CommandUtils.displayName(target);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TransferManager.TransferResult result = transferManager.transfer(
                    player.getUniqueId(), target.getUniqueId(), internal, TransferManager.TransferType.ATM_TRANSFER);
            if (result.success()) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.PAY_SENT,
                        "amount", config.format(result.amount()),
                        "player", targetName,
                        "fee", config.format(result.fee())));
            } else {
                String reason = result.failReason();
                if ("SENDER_FROZEN".equals(reason)) {
                    CommandUtils.msg(sender, MessageConfig.SENDER_FROZEN);
                } else if (reason != null && reason.startsWith("RECEIVER_FROZEN")) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.RECEIVER_FROZEN, "player", targetName));
                } else if (reason != null && reason.startsWith("INSUFFICIENT")) {
                    CommandUtils.msg(sender, MessageConfig.format(MessageConfig.INSUFFICIENT_FUNDS,
                            "amount", config.format(internal), "balance", "?"));
                } else {
                    CommandUtils.msg(sender, "&c送金に失敗しました。");
                }
            }
        });
        return true;
    }

    private boolean cmdUnfreeze(CommandSender sender) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = freezeManager.unfreeze(player.getUniqueId(), true);
            if (error == null) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.UNFREEZE_SELF,
                        "fee", config.format(config.getUnfreezeFee())));
            } else if (error.equals("NOT_FROZEN")) {
                CommandUtils.msg(sender, MessageConfig.UNFREEZE_NOT_FROZEN);
            } else if (error.equals("LOAN_OVERDUE")) {
                CommandUtils.msg(sender, "&cローン延滞による凍結は /loan repay で返済しないと解除できません。");
            } else if (error.startsWith("INSUFFICIENT")) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.UNFREEZE_INSUFFICIENT,
                        "fee", config.format(config.getUnfreezeFee())));
            } else {
                CommandUtils.msg(sender, "&c凍結解除に失敗しました: " + error);
            }
        });
        return true;
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6&l銀行コマンド &8&m----");
        CommandUtils.msg(sender, "&e/bank show &7- 口座情報を表示");
        CommandUtils.msg(sender, "&e/bank deposit &7- 現金アイテムを入金");
        CommandUtils.msg(sender, "&e/bank withdraw <金額> &7- 現金アイテムとして出金");
        CommandUtils.msg(sender, "&e/bank transfer <プレイヤー> <金額> &7- 送金（ATM手数料あり）");
        CommandUtils.msg(sender, "&e/bank unfreeze &7- 口座凍結を解除（手数料あり）");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        String partial = args[args.length - 1];
        if (args.length == 1)
            return CommandUtils.filter(Arrays.asList("show", "deposit", "withdraw", "transfer", "unfreeze"), partial);
        if (args.length == 2 && args[0].equalsIgnoreCase("transfer"))
            return filterOnlinePlayers(partial);
        return List.of();
    }

    private static List<String> filterOnlinePlayers(String partial) {
        String lower = partial.toLowerCase();
        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .collect(java.util.stream.Collectors.toList());
    }

    private int estimateItemCount(long amount) {
        int count = 0;
        long remaining = amount;
        for (EconomyConfig.Denomination d : config.getDenominations()) {
            long denom = d.amount();
            long stacks = remaining / denom / 64;
            count += (int) stacks;
            if (remaining / denom % 64 > 0) count++;
            remaining %= denom;
        }
        return count + 1;
    }

    private int countFreeSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null) free++;
        }
        return free;
    }
}
