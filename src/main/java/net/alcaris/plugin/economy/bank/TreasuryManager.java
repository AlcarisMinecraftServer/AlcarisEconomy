package net.alcaris.plugin.economy.bank;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class TreasuryManager {

    private final TreasuryRepository repo;
    private final EconomyConfig config;
    private final BalanceRepository balanceRepo;
    private final Logger logger;

    public TreasuryManager(TreasuryRepository repo, EconomyConfig config,
                           BalanceRepository balanceRepo, Logger logger) {
        this.repo = repo;
        this.config = config;
        this.balanceRepo = balanceRepo;
        this.logger = logger;
    }

    public void initializeDefaults() throws SQLException {
        for (EconomyConfig.TreasuryInit init : config.getInitialTreasuries()) {
            repo.initTreasury(init.key(), init.description());
        }
        logger.info("Treasury defaults initialized.");
    }

    public void deposit(String key, long amount, String type, UUID fromUuid, String note) {
        if (amount <= 0) return;
        try {
            if (!repo.exists(key)) {
                logger.warning("[Treasury] Routing key not found: " + key + ", skipping deposit.");
                return;
            }
            repo.deposit(key, amount, type, fromUuid, note);
        } catch (SQLException e) {
            logger.warning("[Treasury] Failed to deposit into " + key + ": " + e.getMessage());
        }
    }

    public void withdraw(String key, long amount, UUID toPlayer, UUID actorUuid) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        repo.withdraw(key, amount, "ADMIN_WITHDRAW", actorUuid, "→ player " + toPlayer);
        balanceRepo.addBalance(toPlayer, amount);
        balanceRepo.updateLastTxnAt(toPlayer);
    }

    public void adminDeposit(String key, long amount, UUID actorUuid) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        repo.deposit(key, amount, "ADMIN_DEPOSIT", actorUuid, null);
    }

    public void transfer(String fromKey, String toKey, long amount) throws SQLException {
        repo.transfer(fromKey, toKey, amount);
    }

    public long getBalance(String key) throws SQLException {
        return repo.getBalance(key);
    }

    public List<TreasuryRepository.TreasuryRow> listAll() throws SQLException {
        return repo.listAll();
    }

    public List<TreasuryRepository.TreasuryLogRow> getLog(String key, int page) throws SQLException {
        return repo.getLog(key, page, 10);
    }

    public boolean exists(String key) throws SQLException {
        return repo.exists(key);
    }
}
