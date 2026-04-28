package net.alcaris.plugin.economy.database;

import net.alcaris.plugin.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class SchemaInitializer {

    private final DatabaseManager dbManager;
    private final Logger logger;

    public SchemaInitializer(DatabaseManager dbManager, Logger logger) {
        this.dbManager = dbManager;
        this.logger = logger;
    }

    public void initialize() throws SQLException {
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `account` (
                    `uuid`        BINARY(16)  NOT NULL PRIMARY KEY,
                    `balance`     BIGINT      NOT NULL DEFAULT 0,
                    `last_txn_at` BIGINT      NOT NULL DEFAULT 0,
                    `is_frozen`   BOOLEAN     NOT NULL DEFAULT FALSE,
                    `frozen_at`   BIGINT      DEFAULT NULL,
                    `created_at`  BIGINT      NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `activity_score` (
                    `uuid`        BINARY(16)  NOT NULL,
                    `week_start`  DATE        NOT NULL,
                    `score`       INTEGER     NOT NULL DEFAULT 0,
                    `combat_pt`   INTEGER     NOT NULL DEFAULT 0,
                    `mining_pt`   INTEGER     NOT NULL DEFAULT 0,
                    `minigame_pt` INTEGER     NOT NULL DEFAULT 0,
                    `chat_pt`     INTEGER     NOT NULL DEFAULT 0,
                    PRIMARY KEY (`uuid`, `week_start`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `interest_log` (
                    `log_id`     BIGINT     PRIMARY KEY AUTO_INCREMENT,
                    `uuid`       BINARY(16) NOT NULL,
                    `week_start` DATE       NOT NULL,
                    `score`      INTEGER    NOT NULL,
                    `multiplier` DOUBLE     NOT NULL,
                    `amount`     BIGINT     NOT NULL,
                    `applied_at` BIGINT     NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `transfer_log` (
                    `log_id`     BIGINT      PRIMARY KEY AUTO_INCREMENT,
                    `from_uuid`  BINARY(16)  DEFAULT NULL,
                    `to_uuid`    BINARY(16)  DEFAULT NULL,
                    `amount`     BIGINT      NOT NULL,
                    `fee`        BIGINT      NOT NULL DEFAULT 0,
                    `type`       VARCHAR(20) NOT NULL,
                    `created_at` BIGINT      NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `treasury` (
                    `key`         VARCHAR(50)  NOT NULL PRIMARY KEY,
                    `balance`     BIGINT       NOT NULL DEFAULT 0,
                    `description` VARCHAR(200) DEFAULT NULL,
                    `updated_at`  BIGINT       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `treasury_log` (
                    `log_id`       BIGINT      PRIMARY KEY AUTO_INCREMENT,
                    `treasury_key` VARCHAR(50) NOT NULL,
                    `from_uuid`    BINARY(16)  DEFAULT NULL,
                    `amount`       BIGINT      NOT NULL,
                    `type`         VARCHAR(30) NOT NULL,
                    `note`         VARCHAR(200) DEFAULT NULL,
                    `created_at`   BIGINT      NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `crypto_asset` (
                    `symbol`       VARCHAR(10) NOT NULL PRIMARY KEY,
                    `display_name` VARCHAR(50) NOT NULL,
                    `current_rate` BIGINT      NOT NULL,
                    `updated_at`   BIGINT      NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `crypto_rate_history` (
                    `history_id`  BIGINT      PRIMARY KEY AUTO_INCREMENT,
                    `symbol`      VARCHAR(10) NOT NULL,
                    `rate`        BIGINT      NOT NULL,
                    `recorded_at` BIGINT      NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `crypto_holding` (
                    `uuid`   BINARY(16)  NOT NULL,
                    `symbol` VARCHAR(10) NOT NULL,
                    `amount` BIGINT      NOT NULL DEFAULT 0,
                    PRIMARY KEY (`uuid`, `symbol`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            createIndexIfAbsent(stmt, "idx_account_frozen",      "account",            "(`is_frozen`, `last_txn_at`)");
            createIndexIfAbsent(stmt, "idx_activity_week",       "activity_score",     "(`week_start`)");
            createIndexIfAbsent(stmt, "idx_transfer_from",       "transfer_log",       "(`from_uuid`, `created_at` DESC)");
            createIndexIfAbsent(stmt, "idx_transfer_to",         "transfer_log",       "(`to_uuid`, `created_at` DESC)");
            createIndexIfAbsent(stmt, "idx_treasury_log_key",    "treasury_log",       "(`treasury_key`, `created_at` DESC)");
            createIndexIfAbsent(stmt, "idx_crypto_rate_history", "crypto_rate_history","(`symbol`, `recorded_at` DESC)");

            try { stmt.executeUpdate("ALTER TABLE `account` ADD COLUMN `freeze_reason` ENUM('INACTIVITY','LOAN_OVERDUE') DEFAULT NULL"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `transfer_log` MODIFY COLUMN `type` VARCHAR(30) NOT NULL"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `transfer_log` ADD COLUMN `cheque_id` BIGINT DEFAULT NULL"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `crypto_asset` ADD COLUMN `trend_state` ENUM('BULL','BEAR','NEUTRAL') NOT NULL DEFAULT 'NEUTRAL'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `crypto_asset` ADD COLUMN `trend_started_at` BIGINT DEFAULT NULL"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `crypto_asset` ADD COLUMN `bubble_phase` ENUM('NORMAL','BUBBLE','CRASH','RECOVERY') NOT NULL DEFAULT 'NORMAL'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `crypto_asset` ADD COLUMN `realized_vol` DOUBLE NOT NULL DEFAULT 0.0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `crypto_asset` ADD COLUMN `buy_price` BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE `crypto_asset` ADD COLUMN `sell_price` BIGINT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `cheque` (
                    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
                    `issuer_uuid` BINARY(16)   NOT NULL,
                    `amount`      BIGINT       NOT NULL,
                    `note`        VARCHAR(100) DEFAULT NULL,
                    `used`        BOOLEAN      NOT NULL DEFAULT FALSE,
                    `used_by`     BINARY(16)   DEFAULT NULL,
                    `used_at`     BIGINT       DEFAULT NULL,
                    `created_at`  BIGINT       NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            createIndexIfAbsent(stmt, "idx_cheque_issuer", "cheque", "(`issuer_uuid`, `created_at` DESC)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `player_loan` (
                    `id`              BIGINT   PRIMARY KEY AUTO_INCREMENT,
                    `lender_uuid`     BINARY(16) NOT NULL,
                    `borrower_uuid`   BINARY(16) NOT NULL,
                    `principal`       BIGINT   NOT NULL,
                    `repay_amount`    BIGINT   NOT NULL,
                    `remaining`       BIGINT   NOT NULL,
                    `collateral_data` MEDIUMTEXT DEFAULT NULL,
                    `status` ENUM('PENDING','ACTIVE','COMPLETED','DEFAULTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
                    `due_at`          BIGINT   NOT NULL,
                    `created_at`      BIGINT   NOT NULL,
                    `completed_at`    BIGINT   DEFAULT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            createIndexIfAbsent(stmt, "idx_player_loan_lender",   "player_loan", "(`lender_uuid`, `status`)");
            createIndexIfAbsent(stmt, "idx_player_loan_borrower", "player_loan", "(`borrower_uuid`, `status`)");
            createIndexIfAbsent(stmt, "idx_player_loan_due",      "player_loan", "(`due_at`, `status`)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `server_loan` (
                    `uuid`             BINARY(16) NOT NULL PRIMARY KEY,
                    `principal`        BIGINT     NOT NULL DEFAULT 0,
                    `interest_debt`    BIGINT     NOT NULL DEFAULT 0,
                    `overdue_days`     INTEGER    NOT NULL DEFAULT 0,
                    `stage` ENUM('NORMAL','OVERDUE_1','OVERDUE_2') NOT NULL DEFAULT 'NORMAL',
                    `autopay_amount`   BIGINT     DEFAULT NULL,
                    `last_interest_at` BIGINT     NOT NULL DEFAULT 0,
                    `created_at`       BIGINT     NOT NULL,
                    `updated_at`       BIGINT     NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `loan_log` (
                    `log_id`    BIGINT   PRIMARY KEY AUTO_INCREMENT,
                    `loan_type` ENUM('PLAYER','SERVER') NOT NULL,
                    `loan_id`   BIGINT   DEFAULT NULL,
                    `uuid`      BINARY(16) NOT NULL,
                    `amount`    BIGINT   NOT NULL,
                    `type`      VARCHAR(30) NOT NULL,
                    `note`      VARCHAR(200) DEFAULT NULL,
                    `created_at` BIGINT  NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            createIndexIfAbsent(stmt, "idx_loan_log_uuid", "loan_log", "(`uuid`, `created_at` DESC)");

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS `schema_meta` (
                    `key`   VARCHAR(64) NOT NULL PRIMARY KEY,
                    `value` VARCHAR(64) NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

            runDecimalRemovalMigration(conn, stmt);

            logger.info("Database schema initialized successfully.");
        }
    }

    private void runDecimalRemovalMigration(Connection conn, Statement stmt) throws SQLException {
        if (isMigrationApplied(conn, "decimal_removed_v1")) return;

        logger.info("Running decimal removal migration (dividing all amounts by 100)...");
        String[] divisions = {
                "UPDATE `account`             SET `balance` = `balance` / 100",
                "UPDATE `treasury`            SET `balance` = `balance` / 100",
                "UPDATE `transfer_log`        SET `amount` = `amount` / 100, `fee` = `fee` / 100",
                "UPDATE `treasury_log`        SET `amount` = `amount` / 100",
                "UPDATE `interest_log`        SET `amount` = `amount` / 100",
                "UPDATE `crypto_asset`        SET `current_rate` = `current_rate` / 100, `buy_price` = `buy_price` / 100, `sell_price` = `sell_price` / 100",
                "UPDATE `crypto_rate_history` SET `rate` = `rate` / 100",
                "UPDATE `crypto_holding`      SET `amount` = `amount` / 100",
                "UPDATE `cheque`              SET `amount` = `amount` / 100",
                "UPDATE `player_loan`         SET `principal` = `principal` / 100, `repay_amount` = `repay_amount` / 100, `remaining` = `remaining` / 100",
                "UPDATE `server_loan`         SET `principal` = `principal` / 100, `interest_debt` = `interest_debt` / 100, `autopay_amount` = `autopay_amount` / 100 WHERE 1",
                "UPDATE `loan_log`            SET `amount` = `amount` / 100",
        };
        for (String sql : divisions) {
            int rows = stmt.executeUpdate(sql);
            logger.info("  applied: " + sql + " (" + rows + " rows)");
        }
        markMigrationApplied(conn, "decimal_removed_v1");
        logger.info("Decimal removal migration completed.");
    }

    private boolean isMigrationApplied(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM `schema_meta` WHERE `key` = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void markMigrationApplied(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO `schema_meta` (`key`, `value`) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, String.valueOf(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    private void createIndexIfAbsent(Statement stmt, String indexName, String tableName, String columns) {
        try {
            stmt.executeUpdate("CREATE INDEX `" + indexName + "` ON `" + tableName + "` " + columns);
        } catch (SQLException e) {
            if (!e.getMessage().contains("Duplicate key name") && !e.getMessage().contains("already exists")) {
                logger.warning("Could not create index " + indexName + ": " + e.getMessage());
            }
        }
    }
}
