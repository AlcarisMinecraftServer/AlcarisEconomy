package net.alcaris.plugin.economy.repository;

import net.alcaris.plugin.core.database.DatabaseManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyncRepository extends AbstractRepository implements BalanceRepository {

    public SyncRepository(DatabaseManager dbManager) {
        super(dbManager);
    }

    @Override
    public boolean hasAccount(UUID uuid) throws SQLException {
        return dbHasAccount(uuid);
    }

    @Override
    public void createAccount(UUID uuid, long initialBalance) throws SQLException {
        dbCreateAccount(uuid, initialBalance);
    }

    @Override
    public void deleteAccount(UUID uuid) throws SQLException {
        dbDeleteAccount(uuid);
    }

    @Override
    public long getBalance(UUID uuid) throws SQLException {
        return dbGetBalance(uuid);
    }

    @Override
    public void setBalance(UUID uuid, long balance) throws SQLException {
        dbSetBalance(uuid, balance);
    }

    @Override
    public void addBalance(UUID uuid, long delta) throws SQLException {
        dbAddBalance(uuid, delta);
    }

    @Override
    public boolean isFrozen(UUID uuid) throws SQLException {
        return dbIsFrozen(uuid);
    }

    @Override
    public void setFrozen(UUID uuid, boolean frozen) throws SQLException {
        dbSetFrozen(uuid, frozen);
    }

    @Override
    public long getLastTxnAt(UUID uuid) throws SQLException {
        return dbGetLastTxnAt(uuid);
    }

    @Override
    public void updateLastTxnAt(UUID uuid) throws SQLException {
        dbUpdateLastTxnAt(uuid);
    }

    @Override
    public List<Map.Entry<UUID, Long>> getTopBalances(int limit) throws SQLException {
        return dbGetTopBalances(limit);
    }

    @Override
    public void flush() {}

    @Override
    public void shutdown() {}
}
