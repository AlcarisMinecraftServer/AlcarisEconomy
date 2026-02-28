package net.alcaris.plugin.economy.interest;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.AbstractRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ActivityTracker {

    private static final int IDX_TOTAL    = 0;
    private static final int IDX_COMBAT   = 1;
    private static final int IDX_MINING   = 2;
    private static final int IDX_MINIGAME = 3;
    private static final int IDX_CHAT     = 4;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final EconomyConfig config;
    private final DatabaseManager dbManager;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, int[]> scoreCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastChatMsg = new ConcurrentHashMap<>();

    private LocalDate currentWeekStart;

    public ActivityTracker(EconomyConfig config, DatabaseManager dbManager, JavaPlugin plugin) {
        this.config = config;
        this.dbManager = dbManager;
        this.logger = plugin.getLogger();
        this.currentWeekStart = getWeekStart();
    }

    public void loadCurrentWeek() {
        String weekStr = currentWeekStart.format(DATE_FMT);
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT `uuid`, `score`, `combat_pt`, `mining_pt`, `minigame_pt`, `chat_pt`" +
                             " FROM `activity_score` WHERE `week_start` = ?")) {
            stmt.setString(1, weekStr);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = AbstractRepository.bytesToUuid(rs.getBytes(1));
                    int[] scores = {
                            rs.getInt(2), rs.getInt(3),
                            rs.getInt(4), rs.getInt(5), rs.getInt(6)
                    };
                    scoreCache.put(uuid, scores);
                }
            }
            logger.info("[ActivityTracker] Loaded " + scoreCache.size() + " activity records for week " + weekStr);
        } catch (SQLException e) {
            logger.warning("[ActivityTracker] Failed to load current week scores: " + e.getMessage());
        }
    }

    public void addScore(UUID uuid, ActivityType type, int points) {
        if (points <= 0) return;

        LocalDate week = getWeekStart();
        if (!week.equals(currentWeekStart)) {
            currentWeekStart = week;
            scoreCache.clear();
        }

        scoreCache.compute(uuid, (k, arr) -> {
            if (arr == null) arr = new int[5];

            int typeIdx;
            int cap;
            switch (type) {
                case COMBAT   -> { typeIdx = IDX_COMBAT;   cap = config.getCombatWeeklyCap(); }
                case MINING   -> { typeIdx = IDX_MINING;   cap = config.getMiningWeeklyCap(); }
                case MINIGAME -> { typeIdx = IDX_MINIGAME; cap = config.getMinigameWeeklyCap(); }
                case CHAT     -> { typeIdx = IDX_CHAT;     cap = config.getChatWeeklyCap(); }
                default       -> { typeIdx = IDX_TOTAL;    cap = config.getTotalWeeklyCap(); }
            }

            int available = Math.min(points, cap - arr[typeIdx]);
            if (available <= 0) return arr;

            int totalAvailable = Math.min(available, config.getTotalWeeklyCap() - arr[IDX_TOTAL]);
            if (totalAvailable <= 0) return arr;

            arr[typeIdx] += totalAvailable;
            arr[IDX_TOTAL] += totalAvailable;
            return arr;
        });
    }

    public boolean checkChatCooldown(UUID uuid, String message) {
        long now = System.currentTimeMillis();
        long cooldownMs = (long) config.getChatCooldownSeconds() * 1000L;

        Long lastTime = lastChatTime.get(uuid);
        String lastMsg = lastChatMsg.get(uuid);

        if (lastTime != null && (now - lastTime) < cooldownMs) return false;
        if (message.equals(lastMsg)) return false;

        lastChatTime.put(uuid, now);
        lastChatMsg.put(uuid, message);
        return true;
    }

    public List<Map.Entry<UUID, int[]>> getAllScores() {
        return new ArrayList<>(scoreCache.entrySet());
    }

    public void flush(LocalDate weekStart) {
        if (scoreCache.isEmpty()) return;
        String weekStr = weekStart.format(DATE_FMT);
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO `activity_score` (`uuid`, `week_start`, `score`, `combat_pt`, `mining_pt`, `minigame_pt`, `chat_pt`)" +
                             " VALUES (?, ?, ?, ?, ?, ?, ?)" +
                             " ON DUPLICATE KEY UPDATE `score`=VALUES(`score`), `combat_pt`=VALUES(`combat_pt`)," +
                             " `mining_pt`=VALUES(`mining_pt`), `minigame_pt`=VALUES(`minigame_pt`), `chat_pt`=VALUES(`chat_pt`)")) {
            for (Map.Entry<UUID, int[]> entry : scoreCache.entrySet()) {
                int[] arr = entry.getValue();
                stmt.setBytes(1, AbstractRepository.uuidToBytes(entry.getKey()));
                stmt.setString(2, weekStr);
                stmt.setInt(3, arr[IDX_TOTAL]);
                stmt.setInt(4, arr[IDX_COMBAT]);
                stmt.setInt(5, arr[IDX_MINING]);
                stmt.setInt(6, arr[IDX_MINIGAME]);
                stmt.setInt(7, arr[IDX_CHAT]);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            logger.warning("[ActivityTracker] Flush failed: " + e.getMessage());
        }
    }

    public void resetCurrentWeek() {
        scoreCache.clear();
    }

    private static LocalDate getWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
