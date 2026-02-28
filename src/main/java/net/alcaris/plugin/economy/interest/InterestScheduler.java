package net.alcaris.plugin.economy.interest;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.loan.ServerLoanRepository;
import net.alcaris.plugin.economy.repository.AbstractRepository;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class InterestScheduler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final BalanceRepository repository;
    private final ActivityTracker activityTracker;
    private final EconomyConfig config;
    private final DatabaseManager dbManager;
    private final JavaPlugin plugin;
    private final Logger logger;
    private ServerLoanRepository serverLoanRepo;

    public InterestScheduler(BalanceRepository repository, ActivityTracker activityTracker,
                             EconomyConfig config, DatabaseManager dbManager, JavaPlugin plugin) {
        this.repository = repository;
        this.activityTracker = activityTracker;
        this.config = config;
        this.dbManager = dbManager;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void setServerLoanRepo(ServerLoanRepository serverLoanRepo) {
        this.serverLoanRepo = serverLoanRepo;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L * 60, 20L * 60);
    }

    private void tick() {
        if (!config.isInterestEnabled()) return;

        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfWeek() != DayOfWeek.MONDAY) return;
        if (now.getHour() != config.getApplyHour()) return;

        LocalDate weekStart = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        try {
            if (hasInterestLog(weekStart)) return;
            applyWeeklyInterest(weekStart);
        } catch (Exception e) {
            logger.severe("[InterestScheduler] Error during interest run: " + e.getMessage());
        }
    }

    private boolean hasInterestLog(LocalDate weekStart) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT 1 FROM `interest_log` WHERE `week_start` = ? LIMIT 1")) {
            stmt.setString(1, weekStart.format(DATE_FMT));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void applyWeeklyInterest(LocalDate weekStart) {
        logger.info("[InterestScheduler] Starting weekly interest for week " + weekStart);

        activityTracker.flush(weekStart);

        int applied = 0;
        long now = System.currentTimeMillis();
        String weekStr = weekStart.format(DATE_FMT);

        for (Map.Entry<UUID, int[]> entry : activityTracker.getAllScores()) {
            UUID uuid = entry.getKey();
            int score = entry.getValue()[0];

            try {
                if (repository.isFrozen(uuid)) continue;

                if (serverLoanRepo != null) {
                    ServerLoanRepository.ServerLoanRow loanRow = serverLoanRepo.findByUuid(uuid);
                    if (loanRow != null && (loanRow.stage().startsWith("OVERDUE"))) continue;
                }

                double multiplier = getMultiplier(score);
                if (multiplier == 0.0) continue;

                long balance = repository.getBalance(uuid);
                long interest = Math.round(balance * config.getBaseRate() * multiplier);
                interest = Math.min(interest, config.getMaxInterestAmount());
                if (interest <= 0) continue;

                repository.addBalance(uuid, interest);
                repository.updateLastTxnAt(uuid);
                logInterest(uuid, weekStr, score, multiplier, interest, now);

                long finalInterest = interest;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    var player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        String msg = net.alcaris.plugin.economy.config.MessageConfig.format(
                                net.alcaris.plugin.economy.config.MessageConfig.INTEREST_RECEIVED,
                                "amount", net.alcaris.plugin.economy.config.EconomyConfig
                                        .formatStatic(finalInterest),
                                "score", String.valueOf(score));
                        player.sendMessage(colorize(msg));
                    }
                });

                applied++;
            } catch (SQLException e) {
                logger.warning("[InterestScheduler] Failed for " + uuid + ": " + e.getMessage());
            }
        }

        activityTracker.resetCurrentWeek();

        logger.info("[InterestScheduler] Interest applied to " + applied + " accounts.");
    }

    private double getMultiplier(int score) {
        if (score <= 0)  return 0.0;
        if (score < 100) return 0.5;
        if (score < 200) return 0.8;
        if (score < 300) return 1.0;
        return 1.2;
    }

    private void logInterest(UUID uuid, String weekStr, int score, double multiplier,
                             long amount, long now) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `interest_log` (`uuid`, `week_start`, `score`, `multiplier`, `amount`, `applied_at`)" +
                             " VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setBytes(1, AbstractRepository.uuidToBytes(uuid));
            stmt.setString(2, weekStr);
            stmt.setInt(3, score);
            stmt.setDouble(4, multiplier);
            stmt.setLong(5, amount);
            stmt.setLong(6, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[InterestScheduler] Failed to log interest for " + uuid + ": " + e.getMessage());
        }
    }

    private static String colorize(String msg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
    }
}
