package net.alcaris.plugin.economy.bank;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.repository.AbstractRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TreasuryRepository extends AbstractRepository {

    public TreasuryRepository(DatabaseManager dbManager) {
        super(dbManager);
    }

    public record TreasuryRow(String key, long balance, String description, long updatedAt) {}

    public record TreasuryLogRow(long logId, String treasuryKey, UUID fromUuid,
                                 long amount, String type, String note, long createdAt) {}

    public void initTreasury(String key, String description) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT IGNORE INTO `treasury` (`key`, `balance`, `description`, `updated_at`) VALUES (?, 0, ?, ?)")) {
            stmt.setString(1, key);
            stmt.setString(2, description);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    public boolean exists(String key) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM `treasury` WHERE `key` = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long getBalance(String key) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `balance` FROM `treasury` WHERE `key` = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                return -1L;
            }
        }
    }

    public List<TreasuryRow> listAll() throws SQLException {
        List<TreasuryRow> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `key`, `balance`, `description`, `updated_at` FROM `treasury` ORDER BY `key`")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new TreasuryRow(
                            rs.getString(1), rs.getLong(2),
                            rs.getString(3), rs.getLong(4)));
                }
            }
        }
        return result;
    }

    public void deposit(String key, long amount, String type, UUID fromUuid, String note) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE `treasury` SET `balance` = `balance` + ?, `updated_at` = ? WHERE `key` = ?")) {
                    stmt.setLong(1, amount);
                    stmt.setLong(2, now);
                    stmt.setString(3, key);
                    stmt.executeUpdate();
                }
                insertLog(conn, key, fromUuid, amount, type, note, now);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void withdraw(String key, long amount, String type, UUID fromUuid, String note) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                long current = getBalance(key);
                if (current < amount) {
                    throw new SQLException("Insufficient treasury funds in '" + key + "'");
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE `treasury` SET `balance` = `balance` - ?, `updated_at` = ? WHERE `key` = ?")) {
                    stmt.setLong(1, amount);
                    stmt.setLong(2, now);
                    stmt.setString(3, key);
                    stmt.executeUpdate();
                }
                insertLog(conn, key, fromUuid, -amount, type, note, now);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void transfer(String fromKey, String toKey, long amount) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                long fromBal = getBalance(fromKey);
                if (fromBal < amount) {
                    throw new SQLException("Insufficient treasury funds in '" + fromKey + "'");
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE `treasury` SET `balance` = `balance` + ?, `updated_at` = ? WHERE `key` = ?")) {
                    stmt.setLong(1, -amount);
                    stmt.setLong(2, now);
                    stmt.setString(3, fromKey);
                    stmt.executeUpdate();
                    stmt.setLong(1, amount);
                    stmt.setString(3, toKey);
                    stmt.executeUpdate();
                }
                insertLog(conn, fromKey, null, -amount, "TRANSFER_OUT", "→ " + toKey, now);
                insertLog(conn, toKey, null, amount, "TRANSFER_IN", "← " + fromKey, now);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<TreasuryLogRow> getLog(String key, int page, int pageSize) throws SQLException {
        List<TreasuryLogRow> result = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `log_id`, `treasury_key`, `from_uuid`, `amount`, `type`, `note`, `created_at`" +
                             " FROM `treasury_log` WHERE `treasury_key` = ? ORDER BY `created_at` DESC LIMIT ? OFFSET ?")) {
            stmt.setString(1, key);
            stmt.setInt(2, pageSize);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] uuidBytes = rs.getBytes(3);
                    UUID fromUuid = uuidBytes != null ? bytesToUuid(uuidBytes) : null;
                    result.add(new TreasuryLogRow(
                            rs.getLong(1), rs.getString(2), fromUuid,
                            rs.getLong(4), rs.getString(5), rs.getString(6), rs.getLong(7)));
                }
            }
        }
        return result;
    }

    private void insertLog(Connection conn, String key, UUID fromUuid, long amount,
                           String type, String note, long now) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO `treasury_log` (`treasury_key`, `from_uuid`, `amount`, `type`, `note`, `created_at`)" +
                        " VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, key);
            stmt.setBytes(2, fromUuid != null ? uuidToBytes(fromUuid) : null);
            stmt.setLong(3, amount);
            stmt.setString(4, type);
            stmt.setString(5, note);
            stmt.setLong(6, now);
            stmt.executeUpdate();
        }
    }
}
