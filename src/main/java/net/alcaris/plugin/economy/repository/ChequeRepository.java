package net.alcaris.plugin.economy.repository;

import net.alcaris.plugin.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChequeRepository extends AbstractRepository {

    public record ChequeRow(long id, UUID issuerUuid, long amount, String note,
                            boolean used, UUID usedBy, long usedAt, long createdAt) {}

    public ChequeRepository(DatabaseManager dbManager) {
        super(dbManager);
    }

    public long insert(UUID issuerUuid, long amount, String note) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `cheque` (`issuer_uuid`, `amount`, `note`, `created_at`) VALUES (?, ?, ?, ?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setBytes(1, uuidToBytes(issuerUuid));
            stmt.setLong(2, amount);
            if (note != null && !note.isBlank()) stmt.setString(3, note); else stmt.setNull(3, java.sql.Types.VARCHAR);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Failed to get generated cheque ID");
    }

    public ChequeRow findById(long id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `id`,`issuer_uuid`,`amount`,`note`,`used`,`used_by`,`used_at`,`created_at` FROM `cheque` WHERE `id`=?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                byte[] usedByBytes = rs.getBytes(6);
                return new ChequeRow(
                        rs.getLong(1), bytesToUuid(rs.getBytes(2)), rs.getLong(3), rs.getString(4),
                        rs.getBoolean(5), usedByBytes != null ? bytesToUuid(usedByBytes) : null,
                        rs.getLong(7), rs.getLong(8));
            }
        }
    }

    public boolean markUsed(long id, UUID usedBy) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `cheque` SET `used`=TRUE,`used_by`=?,`used_at`=? WHERE `id`=? AND `used`=FALSE")) {
            stmt.setBytes(1, uuidToBytes(usedBy));
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setLong(3, id);
            return stmt.executeUpdate() > 0;
        }
    }

    public ChequeRow findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT `id`,`issuer_uuid`,`amount`,`note`,`used`,`used_by`,`used_at`,`created_at` FROM `cheque` WHERE `id`=?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                byte[] usedByBytes = rs.getBytes(6);
                return new ChequeRow(
                        rs.getLong(1), bytesToUuid(rs.getBytes(2)), rs.getLong(3), rs.getString(4),
                        rs.getBoolean(5), usedByBytes != null ? bytesToUuid(usedByBytes) : null,
                        rs.getLong(7), rs.getLong(8));
            }
        }
    }

    public boolean markUsed(Connection conn, long id, UUID usedBy) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE `cheque` SET `used`=TRUE,`used_by`=?,`used_at`=? WHERE `id`=? AND `used`=FALSE")) {
            stmt.setBytes(1, uuidToBytes(usedBy));
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setLong(3, id);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean markVoided(long id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `cheque` SET `used`=TRUE, `used_at`=? WHERE `id`=? AND `used`=FALSE")) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setLong(2, id);
            return stmt.executeUpdate() > 0;
        }
    }

    public List<ChequeRow> findByIssuer(UUID uuid, int limit, int offset) throws SQLException {
        List<ChequeRow> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `id`,`issuer_uuid`,`amount`,`note`,`used`,`used_by`,`used_at`,`created_at`" +
                     " FROM `cheque` WHERE `issuer_uuid`=? ORDER BY `created_at` DESC LIMIT ? OFFSET ?")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] usedByBytes = rs.getBytes(6);
                    list.add(new ChequeRow(
                            rs.getLong(1), bytesToUuid(rs.getBytes(2)), rs.getLong(3), rs.getString(4),
                            rs.getBoolean(5), usedByBytes != null ? bytesToUuid(usedByBytes) : null,
                            rs.getLong(7), rs.getLong(8)));
                }
            }
        }
        return list;
    }
}
