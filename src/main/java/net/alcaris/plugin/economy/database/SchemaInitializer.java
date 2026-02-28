package net.alcaris.plugin.economy.database;

import net.alcaris.plugin.core.database.DatabaseManager;

import java.sql.Connection;
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

            logger.info("Database schema initialized successfully.");
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
