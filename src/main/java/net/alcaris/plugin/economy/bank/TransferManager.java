package net.alcaris.plugin.economy.bank;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

public class TransferManager {

    public enum TransferType {
        REMOTE_PAY, ATM_TRANSFER, INTEREST, FREEZE_FEE, CRYPTO, TREASURY_WITHDRAW,
        CHEQUE_ISSUE, CHEQUE_USE,
        LOAN_BORROW, LOAN_REPAY, LOAN_INTEREST
    }

    public record TransferResult(boolean success, long amount, long fee, String failReason) {}

    private final BalanceRepository repository;
    private final TreasuryManager treasuryManager;
    private final EconomyConfig config;
    private final net.alcaris.plugin.core.database.DatabaseManager dbManager;
    private final Logger logger;

    public TransferManager(BalanceRepository repository, TreasuryManager treasuryManager,
                           EconomyConfig config,
                           net.alcaris.plugin.core.database.DatabaseManager dbManager,
                           Logger logger) {
        this.repository = repository;
        this.treasuryManager = treasuryManager;
        this.config = config;
        this.dbManager = dbManager;
        this.logger = logger;
    }

    public TransferResult transfer(UUID from, UUID to, long amount, TransferType type) {
        if (amount <= 0) {
            return new TransferResult(false, 0, 0, "Amount must be positive");
        }
        if (from != null && from.equals(to)) {
            return new TransferResult(false, 0, 0, "Cannot transfer to yourself");
        }

        try {
            if (from != null && repository.isFrozen(from)) {
                return new TransferResult(false, 0, 0, "SENDER_FROZEN");
            }
            if (to != null && repository.isFrozen(to)) {
                return new TransferResult(false, 0, 0, "RECEIVER_FROZEN");
            }

            long fee = calculateFee(amount, type);
            long totalCost = amount + fee;

            if (from != null) {
                long balance = repository.getBalance(from);
                if (balance < totalCost) {
                    return new TransferResult(false, 0, 0,
                            "INSUFFICIENT:need=" + totalCost + ",have=" + balance);
                }
                repository.addBalance(from, -totalCost);
                repository.updateLastTxnAt(from);
            }

            if (to != null) {
                repository.addBalance(to, amount);
                repository.updateLastTxnAt(to);
            }

            if (fee > 0) {
                String routingKey = config.getTreasuryRouting(type.name().toLowerCase());
                treasuryManager.deposit(routingKey, fee, type.name(), from, null);
            }

            logTransfer(from, to, amount, fee, type);

            return new TransferResult(true, amount, fee, null);

        } catch (SQLException e) {
            logger.severe("[TransferManager] Transfer failed: " + e.getMessage());
            return new TransferResult(false, 0, 0, "DB_ERROR: " + e.getMessage());
        }
    }

    public long calculateFee(long amount, TransferType type) {
        return switch (type) {
            case REMOTE_PAY -> {
                long fee = Math.round(amount * config.getRemoteRate()) + config.getRemoteFlat();
                fee = Math.max(fee, config.getRemoteMinimum());
                fee = Math.min(fee, config.getRemoteMaximum());
                yield fee;
            }
            case ATM_TRANSFER -> config.getAtmFlat();
            default -> 0L;
        };
    }

    public void logTransfer(UUID from, UUID to, long amount, long fee, TransferType type) {
        logTransfer(from, to, amount, fee, type, null);
    }

    public void logTransfer(UUID from, UUID to, long amount, long fee, TransferType type, Long chequeId) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `transfer_log` (`from_uuid`, `to_uuid`, `amount`, `fee`, `type`, `cheque_id`, `created_at`)" +
                             " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setBytes(1, from != null ? net.alcaris.plugin.economy.repository.AbstractRepository.uuidToBytes(from) : null);
            stmt.setBytes(2, to != null ? net.alcaris.plugin.economy.repository.AbstractRepository.uuidToBytes(to) : null);
            stmt.setLong(3, amount);
            stmt.setLong(4, fee);
            stmt.setString(5, type.name());
            if (chequeId != null) stmt.setLong(6, chequeId); else stmt.setNull(6, java.sql.Types.BIGINT);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[TransferManager] Failed to log transfer: " + e.getMessage());
        }
    }
}
