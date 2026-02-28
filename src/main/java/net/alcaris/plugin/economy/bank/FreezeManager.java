package net.alcaris.plugin.economy.bank;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.AbstractRepository;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class FreezeManager {

    private final BalanceRepository repository;
    private final TreasuryManager treasuryManager;
    private final EconomyConfig config;
    private final DatabaseManager dbManager;
    private final JavaPlugin plugin;
    private final Logger logger;

    private LocalDate lastFreezeBatchDate = null;

    public FreezeManager(BalanceRepository repository, TreasuryManager treasuryManager,
                         EconomyConfig config, DatabaseManager dbManager,
                         JavaPlugin plugin) {
        this.repository = repository;
        this.treasuryManager = treasuryManager;
        this.config = config;
        this.dbManager = dbManager;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void startScheduler() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::dailyCheck, 0L, 20L * 60 * 20);
    }

    public void runStartupCheck() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::runFreezeBatch);
    }

    private void dailyCheck() {
        LocalDate today = LocalDate.now();
        if (lastFreezeBatchDate != null && lastFreezeBatchDate.equals(today)) return;
        lastFreezeBatchDate = today;
        runFreezeBatch();
    }

    private void runFreezeBatch() {
        long threshold = System.currentTimeMillis()
                - ((long) config.getInactiveDays() * 86_400_000L);
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `uuid` FROM `account` WHERE `is_frozen` = FALSE AND `last_txn_at` < ?")) {
            stmt.setLong(1, threshold);
            List<UUID> toFreeze = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    toFreeze.add(AbstractRepository.bytesToUuid(rs.getBytes(1)));
                }
            }
            for (UUID uuid : toFreeze) {
                repository.setFrozen(uuid, true);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(colorize(
                            net.alcaris.plugin.economy.config.MessageConfig.ACCOUNT_FROZEN));
                }
            }
            if (!toFreeze.isEmpty()) {
                logger.info("[FreezeManager] Froze " + toFreeze.size() + " inactive accounts.");
            }
        } catch (SQLException e) {
            logger.warning("[FreezeManager] Freeze batch failed: " + e.getMessage());
        }
    }

    public void notifyOnLogin(UUID uuid, Player player) {
        try {
            if (repository.isFrozen(uuid)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(colorize(
                                net.alcaris.plugin.economy.config.MessageConfig.ACCOUNT_FROZEN)));
                return;
            }
            long lastTxn = repository.getLastTxnAt(uuid);
            long daysSince = (System.currentTimeMillis() - lastTxn) / 86_400_000L;
            long inactiveDays = config.getInactiveDays();
            long warnDays = config.getWarningDaysBefore();

            if (daysSince >= inactiveDays - warnDays) {
                long daysLeft = inactiveDays - daysSince;
                String msg = net.alcaris.plugin.economy.config.MessageConfig.format(
                        net.alcaris.plugin.economy.config.MessageConfig.FREEZE_WARNING,
                        "days", String.valueOf(Math.max(0, daysLeft)));
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(colorize(msg)));
            }
        } catch (SQLException e) {
            logger.warning("[FreezeManager] notifyOnLogin failed for " + uuid + ": " + e.getMessage());
        }
    }

    public String unfreeze(UUID uuid, boolean payFee) {
        try {
            if (!repository.isFrozen(uuid)) return "NOT_FROZEN";

            if (payFee) {
                long fee = config.getUnfreezeFee();
                long balance = repository.getBalance(uuid);
                if (balance < fee) {
                    return "INSUFFICIENT:" + fee;
                }
                repository.addBalance(uuid, -fee);
                String routingKey = config.getTreasuryRouting("freeze_fee");
                treasuryManager.deposit(routingKey, fee, "FREEZE_FEE", uuid, null);
            }

            repository.setFrozen(uuid, false);
            repository.updateLastTxnAt(uuid);
            return null;

        } catch (SQLException e) {
            logger.warning("[FreezeManager] unfreeze failed for " + uuid + ": " + e.getMessage());
            return "DB_ERROR";
        }
    }

    public boolean freeze(UUID uuid) {
        try {
            repository.setFrozen(uuid, true);
            return true;
        } catch (SQLException e) {
            logger.warning("[FreezeManager] freeze failed: " + e.getMessage());
            return false;
        }
    }

    private static String colorize(String msg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
    }

    public static UUID bytesToUuid(byte[] b) {
        return AbstractRepository.bytesToUuid(b);
    }
}
