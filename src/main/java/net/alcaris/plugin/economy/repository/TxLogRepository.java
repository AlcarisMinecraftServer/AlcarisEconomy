package net.alcaris.plugin.economy.repository;

import net.alcaris.plugin.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TxLogRepository extends AbstractRepository {

    public record TxLogEntry(long logId, UUID fromUuid, UUID toUuid,
                             long amount, long fee, String type, Long chequeId, long createdAt) {}

    public TxLogRepository(DatabaseManager dbManager) {
        super(dbManager);
    }

    public List<TxLogEntry> findByUuid(UUID uuid, int limit, int offset) throws SQLException {
        List<TxLogEntry> list = new ArrayList<>();
        byte[] bytes = uuidToBytes(uuid);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `log_id`,`from_uuid`,`to_uuid`,`amount`,`fee`,`type`,`cheque_id`,`created_at`" +
                     " FROM `transfer_log` WHERE `from_uuid`=? OR `to_uuid`=?" +
                     " ORDER BY `created_at` DESC LIMIT ? OFFSET ?")) {
            stmt.setBytes(1, bytes);
            stmt.setBytes(2, bytes);
            stmt.setInt(3, limit);
            stmt.setInt(4, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] from = rs.getBytes(2);
                    byte[] to = rs.getBytes(3);
                    long chequeId = rs.getLong(7);
                    list.add(new TxLogEntry(
                            rs.getLong(1),
                            from != null ? bytesToUuid(from) : null,
                            to != null ? bytesToUuid(to) : null,
                            rs.getLong(4), rs.getLong(5), rs.getString(6),
                            rs.wasNull() ? null : chequeId,
                            rs.getLong(8)));
                }
            }
        }
        return list;
    }

    public List<TxLogEntry> findByUuidAndType(UUID uuid, String type, int limit, int offset) throws SQLException {
        List<TxLogEntry> list = new ArrayList<>();
        byte[] bytes = uuidToBytes(uuid);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `log_id`,`from_uuid`,`to_uuid`,`amount`,`fee`,`type`,`cheque_id`,`created_at`" +
                     " FROM `transfer_log` WHERE (`from_uuid`=? OR `to_uuid`=?) AND `type`=?" +
                     " ORDER BY `created_at` DESC LIMIT ? OFFSET ?")) {
            stmt.setBytes(1, bytes);
            stmt.setBytes(2, bytes);
            stmt.setString(3, type);
            stmt.setInt(4, limit);
            stmt.setInt(5, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] from = rs.getBytes(2);
                    byte[] to = rs.getBytes(3);
                    long chequeId = rs.getLong(7);
                    list.add(new TxLogEntry(
                            rs.getLong(1),
                            from != null ? bytesToUuid(from) : null,
                            to != null ? bytesToUuid(to) : null,
                            rs.getLong(4), rs.getLong(5), rs.getString(6),
                            rs.wasNull() ? null : chequeId,
                            rs.getLong(8)));
                }
            }
        }
        return list;
    }

    public int countByUuid(UUID uuid) throws SQLException {
        byte[] bytes = uuidToBytes(uuid);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM `transfer_log` WHERE `from_uuid`=? OR `to_uuid`=?")) {
            stmt.setBytes(1, bytes);
            stmt.setBytes(2, bytes);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
