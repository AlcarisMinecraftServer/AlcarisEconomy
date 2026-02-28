package net.alcaris.plugin.economy.loan;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerLoanService {

    private final BalanceRepository repository;
    private final PlayerLoanRepository loanRepo;
    private final CollateralManager collateralManager;
    private final EconomyConfig config;
    private final JavaPlugin plugin;

    private final Map<Long, Long> pendingLoans = new ConcurrentHashMap<>();

    public PlayerLoanService(BalanceRepository repository, PlayerLoanRepository loanRepo,
                             CollateralManager collateralManager, EconomyConfig config,
                             JavaPlugin plugin) {
        this.repository = repository;
        this.loanRepo = loanRepo;
        this.collateralManager = collateralManager;
        this.config = config;
        this.plugin = plugin;
    }

    public long create(Player lender, OfflinePlayer borrower, long principal, long repayAmount,
                       int durationDays) throws SQLException {
        if (durationDays > config.getLoanMaxDurationDays()) throw new IllegalArgumentException("DURATION_TOO_LONG");
        if (repository.isFrozen(lender.getUniqueId())) throw new IllegalStateException("LENDER_FROZEN");
        long balance = repository.getBalance(lender.getUniqueId());
        if (balance < principal) throw new IllegalStateException("INSUFFICIENT");

        long durationMs = (long) durationDays * 86_400_000L;
        long loanId = loanRepo.insert(lender.getUniqueId(), borrower.getUniqueId(), principal, repayAmount, durationMs);

        long expireAt = System.currentTimeMillis() + 120_000L;
        pendingLoans.put(loanId, expireAt);

        Player onlineBorrower = borrower.getPlayer();
        if (onlineBorrower != null) {
            onlineBorrower.sendMessage(colorize("&e[ローン] &f" + lender.getName() + " &eからローンの申請が届いています。" +
                    " &a/loan accept " + loanId + " &eまたは &c/loan deny " + loanId));
        }
        return loanId;
    }

    public String accept(Player borrower, long loanId) throws SQLException {
        Long expireAt = pendingLoans.get(loanId);
        if (expireAt == null || System.currentTimeMillis() > expireAt) {
            pendingLoans.remove(loanId);
            return "EXPIRED";
        }
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null || !row.borrowerUuid().equals(borrower.getUniqueId())) return "NOT_FOUND";
        if (!"PENDING".equals(row.status())) return "NOT_PENDING";

        long balance = repository.getBalance(row.lenderUuid());
        if (balance < row.principal()) return "LENDER_INSUFFICIENT";

        repository.addBalance(row.lenderUuid(), -row.principal());
        repository.updateLastTxnAt(row.lenderUuid());
        repository.addBalance(borrower.getUniqueId(), row.principal());
        repository.updateLastTxnAt(borrower.getUniqueId());

        loanRepo.updateStatus(loanId, "ACTIVE");
        pendingLoans.remove(loanId);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player lenderOnline = Bukkit.getPlayer(row.lenderUuid());
            if (lenderOnline != null) {
                lenderOnline.sendMessage(colorize("&a[ローン] &f" + borrower.getName() + " &aがローンを承諾しました。"));
                org.bukkit.inventory.ItemStack note = LoanNoteItem.create(loanId, borrower.getName(),
                        row.principal(), row.repayAmount(), row.dueAt(),
                        row.collateralData() != null, row.remaining());
                lenderOnline.getInventory().addItem(note).forEach((k, v) ->
                        lenderOnline.getWorld().dropItemNaturally(lenderOnline.getLocation(), v));
            }
        });
        return null;
    }

    public String deny(Player borrower, long loanId) throws SQLException {
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null || !row.borrowerUuid().equals(borrower.getUniqueId())) return "NOT_FOUND";
        if (!"PENDING".equals(row.status())) return "NOT_PENDING";
        loanRepo.updateStatus(loanId, "CANCELLED");
        pendingLoans.remove(loanId);
        return null;
    }

    public String cancel(Player lender, long loanId) throws SQLException {
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null || !row.lenderUuid().equals(lender.getUniqueId())) return "NOT_FOUND";
        if (!"PENDING".equals(row.status())) return "NOT_PENDING";
        loanRepo.updateStatus(loanId, "CANCELLED");
        pendingLoans.remove(loanId);
        return null;
    }

    public String repay(Player borrower, long loanId, long amount) throws SQLException {
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null || !row.borrowerUuid().equals(borrower.getUniqueId())) return "NOT_FOUND";
        if (!"ACTIVE".equals(row.status())) return "NOT_ACTIVE";

        long actual = Math.min(amount, row.remaining());
        long balance = repository.getBalance(borrower.getUniqueId());
        if (balance < actual) return "INSUFFICIENT";

        repository.addBalance(borrower.getUniqueId(), -actual);
        repository.updateLastTxnAt(borrower.getUniqueId());
        repository.addBalance(row.lenderUuid(), actual);
        repository.updateLastTxnAt(row.lenderUuid());

        long newRemaining = row.remaining() - actual;
        loanRepo.updateRemaining(loanId, newRemaining);

        if (newRemaining <= 0) {
            loanRepo.updateStatus(loanId, "COMPLETED");
            Player borrowerOnline = Bukkit.getPlayer(row.borrowerUuid());
            if (borrowerOnline != null) {
                collateralManager.release(loanId, borrowerOnline);
            }
        }
        return null;
    }

    public String collectFromNote(Player lender, long loanId) throws SQLException {
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null || !row.lenderUuid().equals(lender.getUniqueId())) return "NOT_FOUND";
        if (!"ACTIVE".equals(row.status())) return "NOT_ACTIVE";

        long borrowerBalance = repository.getBalance(row.borrowerUuid());
        long toCollect = Math.min(borrowerBalance, row.remaining());
        if (toCollect <= 0) return "NO_FUNDS";

        repository.addBalance(row.borrowerUuid(), -toCollect);
        repository.updateLastTxnAt(row.borrowerUuid());
        repository.addBalance(lender.getUniqueId(), toCollect);
        repository.updateLastTxnAt(lender.getUniqueId());

        long newRemaining = row.remaining() - toCollect;
        loanRepo.updateRemaining(loanId, newRemaining);

        if (newRemaining <= 0) {
            loanRepo.updateStatus(loanId, "COMPLETED");
            Player borrowerOnline = Bukkit.getPlayer(row.borrowerUuid());
            if (borrowerOnline != null) {
                collateralManager.release(loanId, borrowerOnline);
            }
        }
        return null;
    }

    public PlayerLoanRepository getRepo() { return loanRepo; }

    public String adminVoid(long loanId) throws SQLException {
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null) return "NOT_FOUND";
        if (!"PENDING".equals(row.status()) && !"ACTIVE".equals(row.status())) return "ALREADY_FINAL";
        loanRepo.updateStatus(loanId, "CANCELLED");
        pendingLoans.remove(loanId);
        if (row.collateralData() != null) {
            collateralManager.seize(loanId, org.bukkit.Bukkit.getOfflinePlayer(row.lenderUuid()));
        }
        return null;
    }

    private static String colorize(String msg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
    }
}
