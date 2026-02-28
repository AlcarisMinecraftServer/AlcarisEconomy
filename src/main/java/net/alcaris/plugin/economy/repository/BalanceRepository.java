package net.alcaris.plugin.economy.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BalanceRepository {

    boolean hasAccount(UUID uuid) throws SQLException;

    void createAccount(UUID uuid, long initialBalance) throws SQLException;

    void deleteAccount(UUID uuid) throws SQLException;

    long getBalance(UUID uuid) throws SQLException;

    void setBalance(UUID uuid, long balance) throws SQLException;

    void addBalance(UUID uuid, long delta) throws SQLException;

    boolean isFrozen(UUID uuid) throws SQLException;

    void setFrozen(UUID uuid, boolean frozen) throws SQLException;

    long getLastTxnAt(UUID uuid) throws SQLException;

    void updateLastTxnAt(UUID uuid) throws SQLException;

    List<Map.Entry<UUID, Long>> getTopBalances(int limit) throws SQLException;

    void flush();

    void shutdown();
}
