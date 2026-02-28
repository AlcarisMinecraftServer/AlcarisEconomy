package net.alcaris.plugin.economy.loan;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.repository.AbstractRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerLoanRepository extends AbstractRepository {

    public record PlayerLoanRow(long id, UUID lenderUuid, UUID borrowerUuid,
                                long principal, long repayAmount, long remaining,
                                String collateralData, String status,
                                long dueAt, long createdAt, long completedAt) {}

    public PlayerLoanRepository(DatabaseManager dbManager) {
        super(dbManager);
    }

    public long insert(UUID lender, UUID borrower, long principal, long repayAmount,
                       long durationMs) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `player_loan` (`lender_uuid`,`borrower_uuid`,`principal`,`repay_amount`," +
                     "`remaining`,`status`,`due_at`,`created_at`) VALUES (?,?,?,?,?,'PENDING',?,?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setBytes(1, uuidToBytes(lender));
            stmt.setBytes(2, uuidToBytes(borrower));
            stmt.setLong(3, principal);
            stmt.setLong(4, repayAmount);
            stmt.setLong(5, repayAmount);
            stmt.setLong(6, now + durationMs);
            stmt.setLong(7, now);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("Failed to get generated loan ID");
    }

    public PlayerLoanRow findById(long id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `id`,`lender_uuid`,`borrower_uuid`,`principal`,`repay_amount`,`remaining`," +
                     "`collateral_data`,`status`,`due_at`,`created_at`,COALESCE(`completed_at`,0)" +
                     " FROM `player_loan` WHERE `id`=?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return row(rs);
            }
        }
    }

    public List<PlayerLoanRow> findByLender(UUID uuid) throws SQLException {
        return findByColumn("lender_uuid", uuid);
    }

    public List<PlayerLoanRow> findByBorrower(UUID uuid) throws SQLException {
        return findByColumn("borrower_uuid", uuid);
    }

    public List<PlayerLoanRow> findOverdue(long now) throws SQLException {
        List<PlayerLoanRow> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `id`,`lender_uuid`,`borrower_uuid`,`principal`,`repay_amount`,`remaining`," +
                     "`collateral_data`,`status`,`due_at`,`created_at`,COALESCE(`completed_at`,0)" +
                     " FROM `player_loan` WHERE `due_at` < ? AND `status`='ACTIVE'")) {
            stmt.setLong(1, now);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(row(rs));
            }
        }
        return list;
    }

    public void updateStatus(long id, String status) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `player_loan` SET `status`=?,`completed_at`=? WHERE `id`=?")) {
            stmt.setString(1, status);
            if ("COMPLETED".equals(status) || "DEFAULTED".equals(status) || "CANCELLED".equals(status)) {
                stmt.setLong(2, System.currentTimeMillis());
            } else {
                stmt.setNull(2, java.sql.Types.BIGINT);
            }
            stmt.setLong(3, id);
            stmt.executeUpdate();
        }
    }

    public void updateRemaining(long id, long remaining) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `player_loan` SET `remaining`=? WHERE `id`=?")) {
            stmt.setLong(1, remaining);
            stmt.setLong(2, id);
            stmt.executeUpdate();
        }
    }

    public void updateCollateralData(long id, String data) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `player_loan` SET `collateral_data`=? WHERE `id`=?")) {
            if (data != null) stmt.setString(1, data); else stmt.setNull(1, java.sql.Types.LONGVARCHAR);
            stmt.setLong(2, id);
            stmt.executeUpdate();
        }
    }

    private List<PlayerLoanRow> findByColumn(String col, UUID uuid) throws SQLException {
        List<PlayerLoanRow> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `id`,`lender_uuid`,`borrower_uuid`,`principal`,`repay_amount`,`remaining`," +
                     "`collateral_data`,`status`,`due_at`,`created_at`,COALESCE(`completed_at`,0)" +
                     " FROM `player_loan` WHERE `" + col + "`=? ORDER BY `created_at` DESC")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(row(rs));
            }
        }
        return list;
    }

    private PlayerLoanRow row(ResultSet rs) throws SQLException {
        return new PlayerLoanRow(
                rs.getLong(1), bytesToUuid(rs.getBytes(2)), bytesToUuid(rs.getBytes(3)),
                rs.getLong(4), rs.getLong(5), rs.getLong(6),
                rs.getString(7), rs.getString(8), rs.getLong(9), rs.getLong(10), rs.getLong(11));
    }
}
