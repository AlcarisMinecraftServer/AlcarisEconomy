package net.alcaris.plugin.economy.gui;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.currency.CashItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public class BankDepositUI extends AbstractBankUI {

    private static final String TITLE = "§6§l入金";

    private final Player player;
    private final EconomyConfig config;
    private final long accountBalance;
    private volatile boolean depositProcessed = false;

    private BankDepositUI(AlcarisEconomy plugin, Player player, long accountBalance) {
        super(plugin, TITLE, 4, null);
        this.player = player;
        this.config = plugin.getEconomyConfig();
        this.accountBalance = accountBalance;
        buildLayout();
        updateHeader();
    }

    public static void openAsync(AlcarisEconomy plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long balance = plugin.getRepository().getBalance(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BankDepositUI ui = new BankDepositUI(plugin, player, balance);
                    player.openInventory(ui.getInventory());
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("[BankDepositUI] openAsync failed: " + e.getMessage());
            }
        });
    }

    private void buildLayout() {
        for (int s : new int[]{27, 28, 29, 30, 31, 33}) {
            setButton(s, makeFiller(Material.BLACK_STAINED_GLASS_PANE));
        }
        setButton(32, item(Material.GRAY_STAINED_GLASS_PANE, "&7戻る",
                java.util.List.of("&7現金をインベントリに返却して戻ります")), this::doBack);
        setButton(34, item(Material.RED_STAINED_GLASS_PANE, "&cCLEAR",
                java.util.List.of("&7スロットの現金を全て返却します")), this::doClear);
        setButton(35, item(Material.LIME_STAINED_GLASS_PANE, "&a&l入金"), this::doDeposit);
    }

    private void updateHeader() {
        long cashTotal = calculateCashInSlots();
        ItemStack header = item(Material.CYAN_STAINED_GLASS_PANE,
                "&b口座残高: &f" + config.format(accountBalance)
                        + "  &b置いた現金: &f" + config.format(cashTotal));
        for (int i = 0; i < 9; i++) inventory.setItem(i, header);
    }

    private long calculateCashInSlots() {
        long total = 0;
        for (int i = 9; i <= 26; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;
            if (CashItem.hasCashMarker(item)) {
                total += (long) CashItem.getAmount(item) * item.getAmount() * EconomyConfig.MULTIPLIER;
            }
        }
        return total;
    }

    private void doBack() {
        depositProcessed = true;
        returnCashSlots();
        BankMainUI.openAsync(plugin, player);
    }

    private void doClear() {
        returnCashSlots();
        updateHeader();
    }

    private void returnCashSlots() {
        for (int i = 9; i <= 26; i++) {
            ItemStack it = inventory.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                inventory.setItem(i, null);
                player.getInventory().addItem(it).forEach((k, v) ->
                        player.getWorld().dropItemNaturally(player.getLocation(), v));
            }
        }
    }

    private void doDeposit() {
        depositProcessed = true;
        depositAndOpenMain();
    }

    private void depositAndOpenMain() {
        Set<Integer> validAmounts = CashItem.buildValidAmounts(config.getDenominations());
        String serverKey = config.getServerKey();
        long total = 0;

        for (int i = 9; i <= 26; i++) {
            ItemStack it = inventory.getItem(i);
            if (it == null) continue;
            if (CashItem.hasCashMarker(it)) {
                if (CashItem.isValid(it, serverKey, validAmounts)) {
                    total += (long) CashItem.getAmount(it) * it.getAmount() * EconomyConfig.MULTIPLIER;
                } else {
                    plugin.getLogger().warning("[BankDepositUI] Forged item removed from " + player.getName());
                }
                inventory.setItem(i, null);
            }
        }

        final long finalTotal = total;
        final UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (finalTotal > 0) {
                try {
                    plugin.getRepository().addBalance(uuid, finalTotal);
                    plugin.getRepository().updateLastTxnAt(uuid);
                } catch (SQLException e) {
                    plugin.getLogger().warning("[BankDepositUI] deposit DB failed: " + e.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> BankMainUI.openAsync(plugin, player));
        });
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (rawSlot >= inventory.getSize()) {
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && CashItem.hasCashMarker(current)) {
                    Bukkit.getScheduler().runTask(plugin, this::updateHeader);
                } else {
                    event.setCancelled(true);
                }
            } else {
                event.setCancelled(true);
            }
            return;
        }

        if (rawSlot < 9 || rawSlot >= 27) {
            event.setCancelled(true);
            Runnable action = actions.get(rawSlot);
            if (action != null) action.run();
            return;
        }

        switch (event.getClick()) {
            case DOUBLE_CLICK, NUMBER_KEY, DROP, CONTROL_DROP -> {
                event.setCancelled(true);
                return;
            }
            default -> {}
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR && !CashItem.hasCashMarker(cursor)) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::updateHeader);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (depositProcessed) return;
        depositProcessed = true;
        depositAndOpenMain();
    }
}
