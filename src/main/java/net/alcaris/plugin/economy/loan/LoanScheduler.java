package net.alcaris.plugin.economy.loan;

import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.bank.TreasuryManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

public class LoanScheduler {

    private final BalanceRepository repository;
    private final PlayerLoanRepository playerLoanRepo;
    private final ServerLoanRepository serverLoanRepo;
    private final TreasuryManager treasuryManager;
    private final FreezeManager freezeManager;
    private final EconomyConfig config;
    private final JavaPlugin plugin;
    private final Logger logger;

    public LoanScheduler(BalanceRepository repository, PlayerLoanRepository playerLoanRepo,
                         ServerLoanRepository serverLoanRepo, TreasuryManager treasuryManager,
                         FreezeManager freezeManager, EconomyConfig config, JavaPlugin plugin) {
        this.repository = repository;
        this.playerLoanRepo = playerLoanRepo;
        this.serverLoanRepo = serverLoanRepo;
        this.treasuryManager = treasuryManager;
        this.freezeManager = freezeManager;
        this.config = config;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L * 60, 20L * 60);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long oneDayMs = 86_400_000L;

        try {
            processPlayerLoans(now);
        } catch (Exception e) {
            logger.severe("[LoanScheduler] Player loan tick error: " + e.getMessage());
        }

        try {
            processServerLoans(now, oneDayMs);
        } catch (Exception e) {
            logger.severe("[LoanScheduler] Server loan tick error: " + e.getMessage());
        }
    }

    private void processPlayerLoans(long now) throws SQLException {
        List<PlayerLoanRepository.PlayerLoanRow> overdueLoans = playerLoanRepo.findOverdue(now);
        for (PlayerLoanRepository.PlayerLoanRow row : overdueLoans) {
            if (!"ACTIVE".equals(row.status())) continue;

            long cap = Math.round(row.repayAmount() * config.getLoanOverdueInterestCap());
            if (row.remaining() >= cap) continue;

            long overdueInterest = Math.round(row.remaining() * config.getLoanOverdueInterestRate());
            if (overdueInterest <= 0) continue;
            long newRemaining = Math.min(row.remaining() + overdueInterest, cap);
            playerLoanRepo.updateRemaining(row.id(), newRemaining);

            Bukkit.getScheduler().runTask(plugin, () -> {
                org.bukkit.entity.Player borrower = Bukkit.getPlayer(row.borrowerUuid());
                if (borrower != null) {
                    borrower.sendMessage(colorize("&c[ローン] ローンが期限超過です。延滞利息が加算されました。"));
                }
                org.bukkit.entity.Player lender = Bukkit.getPlayer(row.lenderUuid());
                if (lender != null) {
                    lender.sendMessage(colorize("&e[ローン] 貸付ローンが期限超過になりました。"));
                }
            });
        }
    }

    private void processServerLoans(long now, long oneDayMs) throws SQLException {
        List<ServerLoanRepository.ServerLoanRow> activeLoans = serverLoanRepo.findAllActive();
        for (ServerLoanRepository.ServerLoanRow row : activeLoans) {
            if (row.lastInterestAt() + oneDayMs > now) continue;

            double rate = getDailyRate(row.stage());
            long totalDebt = row.principal() + row.interestDebt();
            long interest = Math.round(totalDebt * rate);
            if (interest <= 0) interest = 1;

            long balance = repository.getBalance(row.uuid());
            long autopayAmount = row.autopayAmount() != null ? row.autopayAmount() : 0L;
            long toPay = Math.min(autopayAmount, balance);
            long newInterestDebt = row.interestDebt() + interest;
            long newPrincipal = row.principal();
            int newOverdueDays = row.overdueDays();
            String newStage = row.stage();

            if (toPay > 0) {
                repository.addBalance(row.uuid(), -toPay);
                repository.updateLastTxnAt(row.uuid());
                if (toPay >= newInterestDebt) {
                    toPay -= newInterestDebt;
                    newInterestDebt = 0;
                } else {
                    newInterestDebt -= toPay;
                    toPay = 0;
                }
                newPrincipal = Math.max(0, newPrincipal - toPay);
                treasuryManager.deposit(config.getLoanServerTreasuryKey(), toPay + (row.interestDebt() + interest - newInterestDebt),
                        "LOAN_INTEREST", row.uuid(), null);
            } else {
                newOverdueDays++;
            }

            boolean fullyPaid = newPrincipal <= 0 && newInterestDebt <= 0;
            if (fullyPaid) {
                newStage = "COMPLETED";
                serverLoanRepo.update(row.uuid(), 0, 0, 0, newStage, now);
                freezeManager.unfreeze(row.uuid(), false);
            } else {
                if (newOverdueDays >= config.getLoanOverdue2Days() && !"OVERDUE_2".equals(newStage)) {
                    newStage = "OVERDUE_2";
                } else if (newOverdueDays >= config.getLoanOverdue1Days() && "NORMAL".equals(newStage)) {
                    newStage = "OVERDUE_1";
                    freezeManager.freeze(row.uuid(), FreezeManager.FreezeReason.LOAN_OVERDUE);
                }
                serverLoanRepo.update(row.uuid(), newPrincipal, newInterestDebt, newOverdueDays, newStage, now);
            }

            final String stageFinal = newStage;
            final long interestFinal = interest;
            Bukkit.getScheduler().runTask(plugin, () -> {
                org.bukkit.entity.Player online = Bukkit.getPlayer(row.uuid());
                if (online != null) {
                    if ("COMPLETED".equals(stageFinal)) {
                        online.sendMessage(colorize("&a[サーバーローン] ローンが完済されました。"));
                    } else if ("OVERDUE_1".equals(stageFinal) || "OVERDUE_2".equals(stageFinal)) {
                        online.sendMessage(colorize("&c[サーバーローン] ローンが延滞中です。利息: "
                                + net.alcaris.plugin.economy.config.EconomyConfig.formatStatic(interestFinal)));
                    } else {
                        online.sendMessage(colorize("&e[サーバーローン] 日次利息が加算されました: "
                                + net.alcaris.plugin.economy.config.EconomyConfig.formatStatic(interestFinal)));
                    }
                }
            });
        }
    }

    private double getDailyRate(String stage) {
        return switch (stage) {
            case "OVERDUE_1" -> config.getLoanOverdue1Rate();
            case "OVERDUE_2" -> config.getLoanOverdue2Rate();
            default          -> config.getLoanServerDailyRate();
        };
    }

    private static String colorize(String msg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
    }
}
