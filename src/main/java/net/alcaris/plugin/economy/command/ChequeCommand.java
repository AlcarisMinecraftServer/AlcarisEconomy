package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.currency.ChequeService;
import net.alcaris.plugin.economy.repository.ChequeRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ChequeCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;

    private final AlcarisEconomy plugin;
    private final ChequeService chequeService;
    private final ChequeRepository chequeRepository;
    private final EconomyConfig config;
    private final Logger logger;

    public ChequeCommand(AlcarisEconomy plugin, ChequeService chequeService,
                         ChequeRepository chequeRepository) {
        this.plugin = plugin;
        this.chequeService = chequeService;
        this.chequeRepository = chequeRepository;
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.cheque")) return true;
        if (args.length == 0) return showHelp(sender);

        return switch (args[0].toLowerCase()) {
            case "issue"  -> cmdIssue(sender, args);
            case "list"   -> cmdList(sender, args);
            default       -> showHelp(sender);
        };
    }

    private boolean cmdIssue(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 2) {
            CommandUtils.msg(sender, "&c使い方: /cheque issue <金額> [メモ]");
            return true;
        }
        double amount = CommandUtils.parsePositiveDouble(args[1]);
        if (amount < 0) { CommandUtils.msg(sender, "&c無効な金額です。"); return true; }
        long internal = CommandUtils.toInternal(amount);

        String note = null;
        if (args.length >= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) sb.append(' ');
                sb.append(args[i]);
            }
            note = sb.toString();
            if (note.length() > config.getChequeMaxNoteLength()) {
                CommandUtils.msg(sender, "&cメモが長すぎます（最大" + config.getChequeMaxNoteLength() + "文字）。");
                return true;
            }
        }

        final String finalNote = note;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ItemStack cheque = chequeService.issue(player, internal, finalNote);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(cheque).forEach((k, v) ->
                            player.getWorld().dropItemNaturally(player.getLocation(), v));
                    CommandUtils.msg(sender, "&a小切手を発行しました: " + config.format(internal));
                });
            } catch (IllegalArgumentException e) {
                String msg = e.getMessage();
                if ("TOO_SMALL".equals(msg)) CommandUtils.msg(sender, "&c金額が小さすぎます（最小: " + config.format(config.getChequeMinAmount()) + "）。");
                else if ("TOO_LARGE".equals(msg)) CommandUtils.msg(sender, "&c金額が大きすぎます（最大: " + config.format(config.getChequeMaxAmount()) + "）。");
                else CommandUtils.msg(sender, "&c発行に失敗しました: " + msg);
            } catch (IllegalStateException e) {
                String msg = e.getMessage();
                switch (msg) {
                    case "DISABLED" -> CommandUtils.msg(sender, "&c小切手機能は無効です。");
                    case "FROZEN" -> CommandUtils.msg(sender, "&cアカウントが凍結されています。");
                    case "INSUFFICIENT" -> CommandUtils.msg(sender, "&c残高が不足しています。");
                    case null, default -> CommandUtils.msg(sender, "&c発行に失敗しました: " + msg);
                }
            } catch (SQLException e) {
                logger.warning("[ChequeCommand] issue failed: " + e.getMessage());
                CommandUtils.msg(sender, "&cDBエラーが発生しました。");
            }
        });
        return true;
    }

    private boolean cmdList(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
        }
        final int finalPage = page;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<ChequeRepository.ChequeRow> rows = chequeRepository.findByIssuer(
                        player.getUniqueId(), PAGE_SIZE, (finalPage - 1) * PAGE_SIZE);
                if (rows.isEmpty()) {
                    CommandUtils.msg(sender, "&7発行した小切手はありません。");
                    return;
                }
                CommandUtils.msg(sender, "&8&m----&r &6小切手一覧 &7(ページ " + finalPage + ") &8&m----");
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
                for (ChequeRepository.ChequeRow row : rows) {
                    String status = row.used() ? "&c使用済み" : "&a未使用";
                    String dateStr = sdf.format(new Date(row.createdAt()));
                    String noteStr = row.note() != null ? " &8[" + row.note() + "]" : "";
                    CommandUtils.msg(sender, "&7#" + row.id() + " " + dateStr + " "
                            + config.format(row.amount()) + " " + status + noteStr);
                }
            } catch (SQLException e) {
                logger.warning("[ChequeCommand] list failed: " + e.getMessage());
            }
        });
        return true;
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6小切手コマンド &8&m----");
        CommandUtils.msg(sender, "&e/cheque issue <金額> [メモ] &7- 小切手を発行");
        CommandUtils.msg(sender, "&e/cheque list [ページ] &7- 発行済み小切手一覧");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("alcariseconomy.cheque")) return List.of();
        if (args.length == 1)
            return CommandUtils.filter(Arrays.asList("issue", "list"), args[0]);
        return List.of();
    }
}
