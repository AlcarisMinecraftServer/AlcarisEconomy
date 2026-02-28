package net.alcaris.plugin.economy.loan;

import net.alcaris.plugin.economy.AlcarisEconomy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

public class LoanNoteListener implements Listener {

    private final AlcarisEconomy plugin;
    private final PlayerLoanService loanService;

    public LoanNoteListener(AlcarisEconomy plugin, PlayerLoanService loanService) {
        this.plugin = plugin;
        this.loanService = loanService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!LoanNoteItem.hasLoanMarker(item)) return;

        long loanId = LoanNoteItem.getLoanId(item);
        if (loanId < 0) return;

        event.setCancelled(true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String err = null;
            try {
                err = loanService.collectFromNote(player, loanId);
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("[LoanNoteListener] collectFromNote failed: " + e.getMessage());
                err = "DB_ERROR";
            }
            final String finalErr = err;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalErr == null) {
                    player.sendMessage(colorize("&a[ローン] 返済を回収しました。"));
                    updateNote(player, loanId);
                } else if (finalErr.equals("NOT_FOUND")) {
                    player.sendMessage(colorize("&c[ローン] ローンが見つかりません。"));
                } else if (finalErr.equals("NOT_ACTIVE")) {
                    player.sendMessage(colorize("&c[ローン] このローンはすでに完了または無効です。"));
                } else if (finalErr.equals("NO_FUNDS")) {
                    player.sendMessage(colorize("&e[ローン] 債務者の残高がありません。"));
                } else {
                    player.sendMessage(colorize("&c[ローン] エラー: " + finalErr));
                }
            });
        });
    }

    private void updateNote(Player player, long loanId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerLoanRepository.PlayerLoanRow row = loanService.getRepo().findById(loanId);
                if (row == null) return;
                String borrowerName = org.bukkit.Bukkit.getOfflinePlayer(row.borrowerUuid()).getName();
                if (borrowerName == null) borrowerName = row.borrowerUuid().toString();
                final ItemStack updated = LoanNoteItem.create(loanId, borrowerName,
                        row.principal(), row.repayAmount(), row.dueAt(),
                        row.collateralData() != null, row.remaining());
                final ItemStack updated2 = updated;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (LoanNoteItem.hasLoanMarker(held) && LoanNoteItem.getLoanId(held) == loanId) {
                        if ("COMPLETED".equals(row.status())) {
                            player.getInventory().setItemInMainHand(null);
                        } else {
                            player.getInventory().setItemInMainHand(updated2);
                        }
                    }
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("[LoanNoteListener] updateNote failed: " + e.getMessage());
            }
        });
    }

    private static String colorize(String msg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
    }
}
