package net.alcaris.plugin.economy.loan;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class CollateralManager {

    private final JavaPlugin plugin;
    private final PlayerLoanRepository loanRepo;

    public CollateralManager(JavaPlugin plugin, PlayerLoanRepository loanRepo) {
        this.plugin = plugin;
        this.loanRepo = loanRepo;
    }

    public void save(long loanId, List<ItemStack> items) throws SQLException {
        byte[] bytes = ItemStack.serializeItemsAsBytes(items.toArray(new ItemStack[0]));
        String encoded = Base64.getEncoder().encodeToString(bytes);
        loanRepo.updateCollateralData(loanId, encoded);
    }

    public void release(long loanId, Player borrower) throws SQLException {
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null || row.collateralData() == null) return;
        List<ItemStack> items = deserialize(row.collateralData());
        for (ItemStack item : items) {
            borrower.getInventory().addItem(item).forEach((k, v) ->
                    borrower.getWorld().dropItemNaturally(borrower.getLocation(), v));
        }
        loanRepo.updateCollateralData(loanId, null);
    }

    public void seize(long loanId, OfflinePlayer lender) throws SQLException {
        PlayerLoanRepository.PlayerLoanRow row = loanRepo.findById(loanId);
        if (row == null || row.collateralData() == null) return;
        Player online = lender.getPlayer();
        if (online != null) {
            List<ItemStack> items = deserialize(row.collateralData());
            for (ItemStack item : items) {
                online.getInventory().addItem(item).forEach((k, v) ->
                        online.getWorld().dropItemNaturally(online.getLocation(), v));
            }
        }
        loanRepo.updateCollateralData(loanId, null);
    }

    private List<ItemStack> deserialize(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            ItemStack[] arr = ItemStack.deserializeItemsFromBytes(bytes);
            List<ItemStack> list = new ArrayList<>();
            for (ItemStack item : arr) {
                if (item != null) list.add(item);
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
