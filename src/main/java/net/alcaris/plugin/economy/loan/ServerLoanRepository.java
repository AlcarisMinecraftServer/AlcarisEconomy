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

public class ServerLoanRepository extends AbstractRepository {

    public record ServerLoanRow(UUID uuid, long principal, long interestDebt, int overdueDays,
                                String stage, Long autopayAmount, long lastInterestAt,
                                long createdAt, long updatedAt) {}

    public ServerLoanRepository(DatabaseManager dbManager) {
        super(dbManager);
    }

    public ServerLoanRow findByUuid(UUID uuid) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `uuid`,`principal`,`interest_debt`,`overdue_days`,`stage`," +
                     "`autopay_amount`,`last_interest_at`,`created_at`,`updated_at`" +
                     " FROM `server_loan` WHERE `uuid`=?")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                long autopay = rs.getLong(6);
                return new ServerLoanRow(
                        bytesToUuid(rs.getBytes(1)), rs.getLong(2), rs.getLong(3),
                        rs.getInt(4), rs.getString(5),
                        rs.wasNull() ? null : autopay,
                        rs.getLong(7), rs.getLong(8), rs.getLong(9));
            }
        }
    }

    public List<ServerLoanRow> findAllActive() throws SQLException {
        List<ServerLoanRow> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `uuid`,`principal`,`interest_debt`,`overdue_days`,`stage`," +
                     "`autopay_amount`,`last_interest_at`,`created_at`,`updated_at`" +
                     " FROM `server_loan` WHERE `principal`>0 OR `interest_debt`>0")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long autopay = rs.getLong(6);
                    list.add(new ServerLoanRow(
                            bytesToUuid(rs.getBytes(1)), rs.getLong(2), rs.getLong(3),
                            rs.getInt(4), rs.getString(5),
                            rs.wasNull() ? null : autopay,
                            rs.getLong(7), rs.getLong(8), rs.getLong(9)));
                }
            }
        }
        return list;
    }

    public void upsert(UUID uuid, long principal, long interestDebt) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `server_loan` (`uuid`,`principal`,`interest_debt`,`created_at`,`updated_at`)" +
                     " VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE `principal`=VALUES(`principal`)," +
                     "`interest_debt`=VALUES(`interest_debt`),`updated_at`=VALUES(`updated_at`)")) {
            stmt.setBytes(1, uuidToBytes(uuid));
            stmt.setLong(2, principal);
            stmt.setLong(3, interestDebt);
            stmt.setLong(4, now);
            stmt.setLong(5, now);
            stmt.executeUpdate();
        }
    }

    public void update(UUID uuid, long principal, long interestDebt, int overdueDays,
                       String stage, long lastInterestAt) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `server_loan` SET `principal`=?,`interest_debt`=?,`overdue_days`=?," +
                     "`stage`=?,`last_interest_at`=?,`updated_at`=? WHERE `uuid`=?")) {
            stmt.setLong(1, principal);
            stmt.setLong(2, interestDebt);
            stmt.setInt(3, overdueDays);
            stmt.setString(4, stage);
            stmt.setLong(5, lastInterestAt);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.setBytes(7, uuidToBytes(uuid));
            stmt.executeUpdate();
        }
    }

    public void setAutopay(UUID uuid, Long autopayAmount) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `server_loan` SET `autopay_amount`=?,`updated_at`=? WHERE `uuid`=?")) {
            if (autopayAmount != null) stmt.setLong(1, autopayAmount); else stmt.setNull(1, java.sql.Types.BIGINT);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setBytes(3, uuidToBytes(uuid));
            stmt.executeUpdate();
        }
    }

    public void insertLog(UUID uuid, String loanType, Long loanId, long amount,
                          String type, String note) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `loan_log` (`loan_type`,`loan_id`,`uuid`,`amount`,`type`,`note`,`created_at`)" +
                     " VALUES (?,?,?,?,?,?,?)")) {
            stmt.setString(1, loanType);
            if (loanId != null) stmt.setLong(2, loanId); else stmt.setNull(2, java.sql.Types.BIGINT);
            stmt.setBytes(3, uuidToBytes(uuid));
            stmt.setLong(4, amount);
            stmt.setString(5, type);
            if (note != null) stmt.setString(6, note); else stmt.setNull(6, java.sql.Types.VARCHAR);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }
}
