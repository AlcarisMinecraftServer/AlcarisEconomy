package net.alcaris.plugin.economy.currency;

import net.alcaris.plugin.economy.bank.TransferManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import net.alcaris.plugin.economy.repository.ChequeRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Logger;

public class ChequeService {

    private final BalanceRepository repository;
    private final TransferManager transferManager;
    private final ChequeRepository chequeRepository;
    private final EconomyConfig config;
    private final Logger logger;

    private final Set<UUID> cooldown = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    public ChequeService(BalanceRepository repository, TransferManager transferManager,
                         ChequeRepository chequeRepository, EconomyConfig config, Logger logger) {
        this.repository = repository;
        this.transferManager = transferManager;
        this.chequeRepository = chequeRepository;
        this.config = config;
        this.logger = logger;
    }

    public ItemStack issue(Player issuer, long amount, String note) throws SQLException {
        if (!config.isChequeEnabled()) throw new IllegalStateException("DISABLED");
        if (amount < config.getChequeMinAmount()) throw new IllegalArgumentException("TOO_SMALL");
        if (amount > config.getChequeMaxAmount()) throw new IllegalArgumentException("TOO_LARGE");
        if (repository.isFrozen(issuer.getUniqueId())) throw new IllegalStateException("FROZEN");

        long balance = repository.getBalance(issuer.getUniqueId());
        if (balance < amount) throw new IllegalStateException("INSUFFICIENT");

        repository.addBalance(issuer.getUniqueId(), -amount);
        repository.updateLastTxnAt(issuer.getUniqueId());

        long chequeId = chequeRepository.insert(issuer.getUniqueId(), amount, note);
        transferManager.logTransfer(issuer.getUniqueId(), null, amount, 0,
                TransferManager.TransferType.CHEQUE_ISSUE, chequeId);

        return ChequeItem.create(chequeId, amount, note, issuer.getName());
    }

    public String use(Player user, long chequeId) {
        UUID userUuid = user.getUniqueId();
        if (cooldown.contains(userUuid)) return "COOLDOWN";
        cooldown.add(userUuid);

        try {
            ChequeRepository.ChequeRow row = chequeRepository.findById(chequeId);
            if (row == null) return "NOT_FOUND";
            if (row.used()) return "ALREADY_USED";
            if (row.issuerUuid().equals(userUuid)) return "OWN_CHEQUE";

            boolean ok = chequeRepository.markUsed(chequeId, userUuid);
            if (!ok) return "ALREADY_USED";

            repository.addBalance(userUuid, row.amount());
            repository.updateLastTxnAt(userUuid);
            transferManager.logTransfer(null, userUuid, row.amount(), 0,
                    TransferManager.TransferType.CHEQUE_USE, chequeId);

            return null;
        } catch (SQLException e) {
            logger.warning("[ChequeService] use failed: " + e.getMessage());
            return "DB_ERROR";
        } finally {
            cooldown.remove(userUuid);
        }
    }

    public long getAmount(long chequeId) throws SQLException {
        ChequeRepository.ChequeRow row = chequeRepository.findById(chequeId);
        return row != null ? row.amount() : 0L;
    }
}
