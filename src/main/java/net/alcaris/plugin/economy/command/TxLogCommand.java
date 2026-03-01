package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.TxLogRepository;
import org.bukkit.Bukkit;
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
import java.util.logging.Logger;

public class TxLogCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;

    private final AlcarisEconomy plugin;
    private final TxLogRepository txLogRepo;
    private final EconomyConfig config;
    private final Logger logger;

    public TxLogCommand(AlcarisEconomy plugin, TxLogRepository txLogRepo) {
        this.plugin = plugin;
        this.txLogRepo = txLogRepo;
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;

        String type = null;
        int page = 1;
        for (String arg : args) {
            try {
                page = Math.max(1, Integer.parseInt(arg));
            } catch (NumberFormatException e) {
                type = arg.toUpperCase();
            }
        }

        final String finalType = type;
        final int finalPage = page;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int offset = (finalPage - 1) * PAGE_SIZE;
                List<TxLogRepository.TxLogEntry> entries;
                if (finalType != null) {
                    entries = txLogRepo.findByUuidAndType(player.getUniqueId(), finalType, PAGE_SIZE, offset);
                } else {
                    entries = txLogRepo.findByUuid(player.getUniqueId(), PAGE_SIZE, offset);
                }
                if (entries.isEmpty()) {
                    CommandUtils.msg(sender, "&7取引履歴がありません。");
                    return;
                }
                CommandUtils.msg(sender, "&8&m----&r &6取引履歴 &7(ページ " + finalPage + ") &8&m----");
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
                for (TxLogRepository.TxLogEntry e : entries) {
                    String dir = e.fromUuid() != null && e.fromUuid().equals(player.getUniqueId()) ? "&c-" : "&a+";
                    String timeStr = sdf.format(new Date(e.createdAt()));
                    String typeStr = e.type();
                    String amountStr = config.format(e.amount());
                    String counterpart = resolveCounterpart(e, player);
                    CommandUtils.msg(sender, "&7" + timeStr + " " + dir + amountStr
                            + " &8[" + typeStr + "]" + (counterpart != null ? " &7→ " + counterpart : ""));
                }
            } catch (SQLException ex) {
                logger.warning("[TxLogCommand] failed: " + ex.getMessage());
            }
        });
        return true;
    }

    private String resolveCounterpart(TxLogRepository.TxLogEntry e, Player self) {
        java.util.UUID other = e.fromUuid() != null && e.fromUuid().equals(self.getUniqueId())
                ? e.toUuid() : e.fromUuid();
        if (other == null) return "&8[サーバー]";
        var op = Bukkit.getOfflinePlayer(other);
        return op.getName() != null ? op.getName() : other.toString();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1)
            return CommandUtils.filter(Arrays.asList(
                    "REMOTE_PAY", "ATM_TRANSFER", "INTEREST", "CHEQUE_ISSUE",
                    "CHEQUE_USE", "LOAN_BORROW", "LOAN_REPAY", "CRYPTO"), args[0]);
        return List.of();
    }
}
