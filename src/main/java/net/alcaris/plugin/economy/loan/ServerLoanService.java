package net.alcaris.plugin.economy.loan;

import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.bank.TransferManager;
import net.alcaris.plugin.economy.bank.TreasuryManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public class ServerLoanService {

    private final BalanceRepository repository;
    private final ServerLoanRepository serverLoanRepo;
    private final TreasuryManager treasuryManager;
    private final TransferManager transferManager;
    private final FreezeManager freezeManager;
    private final EconomyConfig config;

    public ServerLoanService(BalanceRepository repository, ServerLoanRepository serverLoanRepo,
                             TreasuryManager treasuryManager, TransferManager transferManager,
                             FreezeManager freezeManager, EconomyConfig config) {
        this.repository = repository;
        this.serverLoanRepo = serverLoanRepo;
        this.treasuryManager = treasuryManager;
        this.transferManager = transferManager;
        this.freezeManager = freezeManager;
        this.config = config;
    }

    public String borrow(Player player, long amount) throws SQLException {
        if (!config.isLoanServerEnabled()) return "DISABLED";
        if (repository.isFrozen(player.getUniqueId())) return "FROZEN";

        ServerLoanRepository.ServerLoanRow existing = serverLoanRepo.findByUuid(player.getUniqueId());
        if (existing != null && (existing.principal() > 0 || existing.interestDebt() > 0)) return "ALREADY_HAS_LOAN";

        long balance = repository.getBalance(player.getUniqueId());
        long limitByMultiplier = Math.round(balance * config.getLoanServerBorrowMultiplier());
        long maxBorrow = Math.min(config.getLoanServerBorrowLimit(), limitByMultiplier);
        if (amount <= 0) return "INVALID_AMOUNT";
        if (amount > maxBorrow) return "EXCEEDS_LIMIT";

        repository.addBalance(player.getUniqueId(), amount);
        repository.updateLastTxnAt(player.getUniqueId());
        serverLoanRepo.upsert(player.getUniqueId(), amount, 0);
        serverLoanRepo.insertLog(player.getUniqueId(), "SERVER", null, amount, "BORROW", null);
        transferManager.logTransfer(null, player.getUniqueId(), amount, 0, TransferManager.TransferType.LOAN_BORROW);
        return null;
    }

    public String repay(Player player, long amount) throws SQLException {
        ServerLoanRepository.ServerLoanRow row = serverLoanRepo.findByUuid(player.getUniqueId());
        if (row == null || (row.principal() <= 0 && row.interestDebt() <= 0)) return "NO_LOAN";

        long totalDebt = row.principal() + row.interestDebt();
        long actual = Math.min(amount, totalDebt);
        long balance = repository.getBalance(player.getUniqueId());
        if (balance < actual) return "INSUFFICIENT";

        repository.addBalance(player.getUniqueId(), -actual);
        repository.updateLastTxnAt(player.getUniqueId());

        long payment = actual;
        long newInterestDebt = row.interestDebt();
        long newPrincipal = row.principal();

        if (payment >= newInterestDebt) {
            payment -= newInterestDebt;
            newInterestDebt = 0;
        } else {
            newInterestDebt -= payment;
            payment = 0;
        }
        newPrincipal = Math.max(0, newPrincipal - payment);

        boolean fullyPaid = newPrincipal <= 0 && newInterestDebt <= 0;
        String newStage = fullyPaid ? "NORMAL" : row.stage();
        int newOverdueDays = fullyPaid ? 0 : row.overdueDays();

        serverLoanRepo.update(player.getUniqueId(), newPrincipal, newInterestDebt,
                newOverdueDays, newStage, row.lastInterestAt());
        treasuryManager.deposit(config.getLoanServerTreasuryKey(), actual,
                "LOAN_REPAY", player.getUniqueId(), null);
        serverLoanRepo.insertLog(player.getUniqueId(), "SERVER", null, actual, "REPAY", null);
        transferManager.logTransfer(player.getUniqueId(), null, actual, 0, TransferManager.TransferType.LOAN_REPAY);

        if (fullyPaid) {
            freezeManager.unfreeze(player.getUniqueId(), false);
        }
        return null;
    }

    public String setAutopay(Player player, Long autopayAmount) throws SQLException {
        ServerLoanRepository.ServerLoanRow row = serverLoanRepo.findByUuid(player.getUniqueId());
        if (row == null || (row.principal() <= 0 && row.interestDebt() <= 0)) return "NO_LOAN";
        serverLoanRepo.setAutopay(player.getUniqueId(), autopayAmount);
        return null;
    }

    public ServerLoanRepository.ServerLoanRow getRow(Player player) throws SQLException {
        return serverLoanRepo.findByUuid(player.getUniqueId());
    }

    public ServerLoanRepository.ServerLoanRow getRowByUuid(UUID uuid) throws SQLException {
        return serverLoanRepo.findByUuid(uuid);
    }

    public String adminForgive(UUID uuid) throws SQLException {
        ServerLoanRepository.ServerLoanRow row = serverLoanRepo.findByUuid(uuid);
        if (row == null) return "NO_LOAN";
        serverLoanRepo.update(uuid, 0, 0, 0, "NORMAL", row.lastInterestAt());
        freezeManager.unfreeze(uuid, false);
        return null;
    }

    public String adminSetStage(UUID uuid, String stage) throws SQLException {
        ServerLoanRepository.ServerLoanRow row = serverLoanRepo.findByUuid(uuid);
        if (row == null) return "NO_LOAN";
        serverLoanRepo.update(uuid, row.principal(), row.interestDebt(), row.overdueDays(),
                stage, row.lastInterestAt());
        return null;
    }
}
