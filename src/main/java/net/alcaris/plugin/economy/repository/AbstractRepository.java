package net.alcaris.plugin.economy.repository;

import net.alcaris.plugin.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractRepository {

    protected final DatabaseManager dbManager;

    protected AbstractRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    protected Connection getConnection() throws SQLException {
        return dbManager.getConnection();
    }

    public static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] b = new byte[16];
        for (int i = 7; i >= 0; i--) { b[i] = (byte) (msb & 0xff); msb >>= 8; }
        for (int i = 15; i >= 8; i--) { b[i] = (byte) (lsb & 0xff); lsb >>= 8; }
        return b;
    }

    public static UUID bytesToUuid(byte[] b) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (b[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (b[i] & 0xff);
        return new UUID(msb, lsb);
    }

    protected boolean dbHasAccount(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM `account` WHERE `uuid` = ?")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    protected void dbCreateAccount(UUID uuid, long initialBalance) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT IGNORE INTO `account` (`uuid`, `balance`, `last_txn_at`, `created_at`) VALUES (?, ?, ?, ?)")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            stmt.setLong(2, initialBalance);
            stmt.setLong(3, now);
            stmt.setLong(4, now);
            stmt.executeUpdate();
        }
    }

    protected void dbDeleteAccount(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM `account` WHERE `uuid` = ?")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            stmt.executeUpdate();
        }
    }

    protected long dbGetBalance(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `balance` FROM `account` WHERE `uuid` = ?")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                throw new SQLException("Account not found: " + uuid);
            }
        }
    }

    protected void dbSetBalance(UUID uuid, long balance) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `account` SET `balance` = ?, `last_txn_at` = ? WHERE `uuid` = ?")) {
            stmt.setLong(1, balance);
            stmt.setLong(2, now);
            stmt.setBytes(3, uuidToBytes(uuid));
            stmt.executeUpdate();
        }
    }

    protected void dbAddBalance(UUID uuid, long delta) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `account` SET `balance` = `balance` + ?, `last_txn_at` = ? WHERE `uuid` = ?")) {
            stmt.setLong(1, delta);
            stmt.setLong(2, now);
            stmt.setBytes(3, uuidToBytes(uuid));
            stmt.executeUpdate();
        }
    }

    protected boolean dbIsFrozen(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `is_frozen` FROM `account` WHERE `uuid` = ?")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getBoolean(1);
                return false;
            }
        }
    }

    protected void dbSetFrozen(UUID uuid, boolean frozen) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `account` SET `is_frozen` = ?, `frozen_at` = ? WHERE `uuid` = ?")) {
            stmt.setBoolean(1, frozen);
            if (frozen) {
                stmt.setLong(2, now);
            } else {
                stmt.setNull(2, java.sql.Types.BIGINT);
            }
            stmt.setBytes(3, uuidToBytes(uuid));
            stmt.executeUpdate();
        }
    }

    protected long dbGetLastTxnAt(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `last_txn_at` FROM `account` WHERE `uuid` = ?")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                return 0L;
            }
        }
    }

    protected void dbUpdateLastTxnAt(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `account` SET `last_txn_at` = ? WHERE `uuid` = ?")) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setBytes(2, uuidToBytes(uuid));
            stmt.executeUpdate();
        }
    }

    protected List<Map.Entry<UUID, Long>> dbGetTopBalances(int limit) throws SQLException {
        List<Map.Entry<UUID, Long>> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `uuid`, `balance` FROM `account` WHERE `is_frozen` = FALSE ORDER BY `balance` DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = bytesToUuid(rs.getBytes(1));
                    long balance = rs.getLong(2);
                    result.add(new AbstractMap.SimpleEntry<>(uuid, balance));
                }
            }
        }
        return result;
    }
}
