package net.alcaris.plugin.economy.crypto.engine;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EconomyIndexFetcher {

    private final DatabaseManager dbManager;
    private final boolean enabled;
    private final double  maxCorrection;
    private final double  transferWeight;
    private final double  onlineWeight;
    private final double  freezeWeight;
    private final long    treasuryStabilityThreshold;
    private final double  treasuryStabilityFactor;

    public EconomyIndexFetcher(DatabaseManager dbManager, EconomyConfig config) {
        this.dbManager = dbManager;
        this.enabled = config.isEconIndexEnabled();
        this.maxCorrection = config.getEconIndexMaxCorrection();
        this.transferWeight = config.getEconIndexTransferWeight();
        this.onlineWeight = config.getEconIndexOnlineWeight();
        this.freezeWeight = config.getEconIndexFreezeWeight();
        this.treasuryStabilityThreshold = config.getEconIndexTreasuryThreshold();
        this.treasuryStabilityFactor = config.getEconIndexTreasuryFactor();
    }

    public double fetchCorrection() throws SQLException {
        if (!enabled) return 0.0;

        long oneHourAgo = System.currentTimeMillis() - 3_600_000L;
        double score = 0.0;
        double weightSum = transferWeight + onlineWeight + freezeWeight;

        long recentTransfer = queryLong(
                "SELECT COALESCE(SUM(`amount`),0) FROM `transfer_log` WHERE `created_at` >= ?",
                oneHourAgo);
        long baselineTransfer = queryLong(
                "SELECT COALESCE(AVG(h),0) FROM (SELECT SUM(`amount`) h FROM `transfer_log`" +
                " WHERE `created_at` >= ? - 86400000 GROUP BY FLOOR(`created_at`/3600000)) t",
                System.currentTimeMillis());
        if (baselineTransfer > 0) {
            double ratio = (double) recentTransfer / baselineTransfer - 1.0;
            score += Math.max(-1.0, Math.min(1.0, ratio)) * transferWeight;
        }

        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Math.max(1, Bukkit.getMaxPlayers());
        score += ((double) online / maxPlayers - 0.5) * 2.0 * onlineWeight;

        long recentFreezes = queryLong(
                "SELECT COUNT(*) FROM `account` WHERE `is_frozen` = TRUE AND `frozen_at` >= ?",
                oneHourAgo);
        score -= Math.min(1.0, recentFreezes / 5.0) * freezeWeight;

        long treasuryBalance = queryLong(
                "SELECT COALESCE(`balance`,0) FROM `treasury` WHERE `key` = 'general'",
                -1);
        if (treasuryBalance >= treasuryStabilityThreshold) {
            score *= treasuryStabilityFactor;
        }

        double correction = (score / weightSum) * maxCorrection;
        return Math.max(-maxCorrection, Math.min(maxCorrection, correction));
    }

    private long queryLong(String sql, long param) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (param >= 0) stmt.setLong(1, param);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            return 0L;
        }
    }
}
