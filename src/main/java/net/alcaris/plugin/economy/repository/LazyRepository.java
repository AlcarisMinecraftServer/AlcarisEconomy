package net.alcaris.plugin.economy.repository;

import net.alcaris.plugin.core.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LazyRepository extends AbstractRepository implements BalanceRepository {

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;

    private final ConcurrentHashMap<UUID, Long> balanceCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtySet = ConcurrentHashMap.newKeySet();
    private final JavaPlugin plugin;
    private BukkitTask flushTask;

    public LazyRepository(DatabaseManager dbManager, JavaPlugin plugin) {
        super(dbManager);
        this.plugin = plugin;
    }

    public void startFlushTask() {
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    @Override
    public boolean hasAccount(UUID uuid) throws SQLException {
        return dbHasAccount(uuid);
    }

    @Override
    public void createAccount(UUID uuid, long initialBalance) throws SQLException {
        dbCreateAccount(uuid, initialBalance);
        balanceCache.put(uuid, initialBalance);
    }

    @Override
    public void deleteAccount(UUID uuid) throws SQLException {
        dbDeleteAccount(uuid);
        balanceCache.remove(uuid);
        dirtySet.remove(uuid);
    }

    @Override
    public long getBalance(UUID uuid) throws SQLException {
        Long cached = balanceCache.get(uuid);
        if (cached != null) return cached;
        long balance = dbGetBalance(uuid);
        balanceCache.put(uuid, balance);
        return balance;
    }

    @Override
    public void setBalance(UUID uuid, long balance) throws SQLException {
        ensureCached(uuid);
        balanceCache.put(uuid, balance);
        dirtySet.add(uuid);
    }

    @Override
    public void addBalance(UUID uuid, long delta) throws SQLException {
        ensureCached(uuid);
        balanceCache.compute(uuid, (k, v) -> (v == null ? 0L : v) + delta);
        dirtySet.add(uuid);
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
    public void flush() {
        Set<UUID> toFlush = new HashSet<>(dirtySet);
        dirtySet.removeAll(toFlush);

        for (UUID uuid : toFlush) {
            Long balance = balanceCache.get(uuid);
            if (balance == null) continue;
            try {
                dbSetBalance(uuid, balance);
            } catch (SQLException e) {
                dirtySet.add(uuid);
                plugin.getLogger().warning("[LazyRepository] Failed to flush balance for " + uuid + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flush();
    }

    private void ensureCached(UUID uuid) throws SQLException {
        if (!balanceCache.containsKey(uuid)) {
            long balance = dbGetBalance(uuid);
            balanceCache.putIfAbsent(uuid, balance);
        }
    }
}
