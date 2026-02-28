package net.alcaris.plugin.economy.crypto;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.bank.TreasuryManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.AbstractRepository;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class CryptoMarket {

    private static final long CRYPTO_MULTIPLIER = 100L;

    private final Map<String, CryptoAsset> assets = new HashMap<>();
    private final RateEngine rateEngine = new RateEngine();
    private final BalanceRepository balanceRepo;
    private final TreasuryManager treasuryManager;
    private final EconomyConfig config;
    private final DatabaseManager dbManager;
    private final JavaPlugin plugin;
    private final Logger logger;

    public CryptoMarket(BalanceRepository balanceRepo, TreasuryManager treasuryManager,
                        EconomyConfig config, DatabaseManager dbManager, JavaPlugin plugin) {
        this.balanceRepo = balanceRepo;
        this.treasuryManager = treasuryManager;
        this.config = config;
        this.dbManager = dbManager;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void initialize() throws SQLException {
        for (EconomyConfig.CryptoAssetConfig ac : config.getCryptoAssets()) {
            CryptoAsset asset = new CryptoAsset(
                    ac.symbol(), ac.displayName(), ac.initialRate(),
                    ac.volatility(), ac.maxRate(), ac.minRate());

            upsertAsset(asset);
            long dbRate = loadRate(ac.symbol());
            if (dbRate > 0) asset.setCurrentRate(dbRate);
            assets.put(ac.symbol(), asset);
        }
        logger.info("[CryptoMarket] Loaded " + assets.size() + " crypto assets.");
        startRateScheduler();
    }

    private void startRateScheduler() {
        long intervalTicks = (long) config.getRateUpdateInterval() * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateRates, intervalTicks, intervalTicks);
    }

    private void updateRates() {
        long now = System.currentTimeMillis();
        for (CryptoAsset asset : assets.values()) {
            long netBuy = asset.consumeNetBuy();
            long newRate = rateEngine.computeNewRate(asset, netBuy);
            asset.setCurrentRate(newRate);
            try {
                saveRate(asset, now);
                recordHistory(asset, newRate, now);
                pruneHistory(asset.getSymbol(), now);
            } catch (SQLException e) {
                logger.warning("[CryptoMarket] Rate update failed for " + asset.getSymbol() + ": " + e.getMessage());
            }
        }
    }

    public record TradeResult(boolean success, long cost, long fee, String failReason) {}

    public TradeResult buy(UUID uuid, String symbol, long quantity) {
        CryptoAsset asset = assets.get(symbol);
        if (asset == null) return new TradeResult(false, 0, 0, "UNKNOWN_SYMBOL");

        long totalCost = quantity * asset.getCurrentRate() / CRYPTO_MULTIPLIER;
        long fee = Math.round(totalCost * config.getCryptoFeeRate());
        long totalWithFee = totalCost + fee;

        try {
            if (balanceRepo.isFrozen(uuid)) return new TradeResult(false, 0, 0, "FROZEN");
            long balance = balanceRepo.getBalance(uuid);
            if (balance < totalWithFee) return new TradeResult(false, 0, 0, "INSUFFICIENT");

            balanceRepo.addBalance(uuid, -totalWithFee);
            balanceRepo.updateLastTxnAt(uuid);
            addHolding(uuid, symbol, quantity);
            asset.recordBuy(totalCost);

            treasuryManager.deposit(config.getRoutingCryptoFee(), fee, "CRYPTO_FEE", uuid, "buy " + symbol);

            return new TradeResult(true, totalCost, fee, null);
        } catch (SQLException e) {
            logger.warning("[CryptoMarket] Buy failed: " + e.getMessage());
            return new TradeResult(false, 0, 0, "DB_ERROR");
        }
    }

    public TradeResult sell(UUID uuid, String symbol, long quantity) {
        CryptoAsset asset = assets.get(symbol);
        if (asset == null) return new TradeResult(false, 0, 0, "UNKNOWN_SYMBOL");

        try {
            if (balanceRepo.isFrozen(uuid)) return new TradeResult(false, 0, 0, "FROZEN");
            long holding = getHolding(uuid, symbol);
            if (holding < quantity) return new TradeResult(false, 0, 0, "INSUFFICIENT_HOLDING");

            long totalValue = quantity * asset.getCurrentRate() / CRYPTO_MULTIPLIER;
            long fee = Math.round(totalValue * config.getCryptoFeeRate());
            long received = totalValue - fee;

            addHolding(uuid, symbol, -quantity);
            balanceRepo.addBalance(uuid, received);
            balanceRepo.updateLastTxnAt(uuid);
            asset.recordSell(totalValue);

            treasuryManager.deposit(config.getRoutingCryptoFee(), fee, "CRYPTO_FEE", uuid, "sell " + symbol);

            return new TradeResult(true, totalValue, fee, null);
        } catch (SQLException e) {
            logger.warning("[CryptoMarket] Sell failed: " + e.getMessage());
            return new TradeResult(false, 0, 0, "DB_ERROR");
        }
    }

    public void adminSetRate(String symbol, long rate) {
        CryptoAsset asset = assets.get(symbol);
        if (asset == null) return;
        long clamped = Math.max(asset.getMinRate(), Math.min(asset.getMaxRate(), rate));
        asset.setCurrentRate(clamped);
        try {
            saveRate(asset, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.warning("[CryptoMarket] adminSetRate save failed: " + e.getMessage());
        }
    }

    public void adminSetEvent(String symbol, double correction) {
        CryptoAsset asset = assets.get(symbol);
        if (asset == null) return;
        asset.setEventCorrection(correction);
    }

    public Map<String, Long> getPortfolio(UUID uuid) throws SQLException {
        Map<String, Long> portfolio = new HashMap<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `symbol`, `amount` FROM `crypto_holding` WHERE `uuid` = ?")) {
            stmt.setBytes(1, AbstractRepository.uuidToBytes(uuid));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    portfolio.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return portfolio;
    }

    public List<long[]> getRateHistory(String symbol, int limit) throws SQLException {
        List<long[]> history = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `rate`, `recorded_at` FROM `crypto_rate_history`" +
                             " WHERE `symbol` = ? ORDER BY `recorded_at` DESC LIMIT ?")) {
            stmt.setString(1, symbol);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new long[]{rs.getLong(1), rs.getLong(2)});
                }
            }
        }
        return history;
    }

    public CryptoAsset getAsset(String symbol)       { return assets.get(symbol); }
    public Collection<CryptoAsset> getAllAssets()     { return assets.values(); }
    public boolean hasAsset(String symbol)            { return assets.containsKey(symbol); }

    private void upsertAsset(CryptoAsset asset) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT IGNORE INTO `crypto_asset` (`symbol`, `display_name`, `current_rate`, `updated_at`)" +
                             " VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, asset.getSymbol());
            stmt.setString(2, asset.getDisplayName());
            stmt.setLong(3, asset.getInitialRate());
            stmt.setLong(4, now);
            stmt.executeUpdate();
        }
    }

    private long loadRate(String symbol) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `current_rate` FROM `crypto_asset` WHERE `symbol` = ?")) {
            stmt.setString(1, symbol);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private void saveRate(CryptoAsset asset, long now) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE `crypto_asset` SET `current_rate` = ?, `updated_at` = ? WHERE `symbol` = ?")) {
            stmt.setLong(1, asset.getCurrentRate());
            stmt.setLong(2, now);
            stmt.setString(3, asset.getSymbol());
            stmt.executeUpdate();
        }
    }

    private void recordHistory(CryptoAsset asset, long rate, long now) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `crypto_rate_history` (`symbol`, `rate`, `recorded_at`) VALUES (?, ?, ?)")) {
            stmt.setString(1, asset.getSymbol());
            stmt.setLong(2, rate);
            stmt.setLong(3, now);
            stmt.executeUpdate();
        }
    }

    private void pruneHistory(String symbol, long now) throws SQLException {
        long cutoff = now - (long) config.getHistoryRetentionHours() * 3_600_000L;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM `crypto_rate_history` WHERE `symbol` = ? AND `recorded_at` < ?")) {
            stmt.setString(1, symbol);
            stmt.setLong(2, cutoff);
            stmt.executeUpdate();
        }
    }

    private long getHolding(UUID uuid, String symbol) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `amount` FROM `crypto_holding` WHERE `uuid` = ? AND `symbol` = ?")) {
            stmt.setBytes(1, AbstractRepository.uuidToBytes(uuid));
            stmt.setString(2, symbol);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private void addHolding(UUID uuid, String symbol, long delta) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `crypto_holding` (`uuid`, `symbol`, `amount`) VALUES (?, ?, ?)" +
                             " ON DUPLICATE KEY UPDATE `amount` = `amount` + VALUES(`amount`)")) {
            stmt.setBytes(1, AbstractRepository.uuidToBytes(uuid));
            stmt.setString(2, symbol);
            stmt.setLong(3, delta);
            stmt.executeUpdate();
        }
    }
}
