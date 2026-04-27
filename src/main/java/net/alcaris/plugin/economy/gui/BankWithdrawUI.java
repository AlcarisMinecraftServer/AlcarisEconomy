package net.alcaris.plugin.economy.gui;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.currency.CashItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class BankWithdrawUI extends AbstractBankUI {

    private static final String TITLE = "§6§l出金";

    private final Player player;
    private final EconomyConfig config;
    private final long balance;
    private final BankNumpadHelper numpad;

    private BankWithdrawUI(AlcarisEconomy plugin, Player player, long balance) {
        super(plugin, TITLE, 4, null);
        this.player = player;
        this.config = plugin.getEconomyConfig();
        this.balance = balance;
        this.numpad = new BankNumpadHelper(balance / EconomyConfig.MULTIPLIER);
        buildLayout();
    }

    public static void openAsync(AlcarisEconomy plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long balance = plugin.getRepository().getBalance(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BankWithdrawUI ui = new BankWithdrawUI(plugin, player, balance);
                    player.openInventory(ui.getInventory());
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("[BankWithdrawUI] openAsync failed: " + e.getMessage());
            }
        });
    }

    private void buildLayout() {
        setButton(0,  numpadKey("7"), () -> { numpad.digit(7); updateHeader(); });
        setButton(1,  numpadKey("8"), () -> { numpad.digit(8); updateHeader(); });
        setButton(2,  numpadKey("9"), () -> { numpad.digit(9); updateHeader(); });

        setButton(9,  numpadKey("4"), () -> { numpad.digit(4); updateHeader(); });
        setButton(10, numpadKey("5"), () -> { numpad.digit(5); updateHeader(); });
        setButton(11, numpadKey("6"), () -> { numpad.digit(6); updateHeader(); });

        setButton(18, numpadKey("1"), () -> { numpad.digit(1); updateHeader(); });
        setButton(19, numpadKey("2"), () -> { numpad.digit(2); updateHeader(); });
        setButton(20, numpadKey("3"), () -> { numpad.digit(3); updateHeader(); });

        setButton(27, item(Material.BARRIER, "&cC",      10006), () -> { numpad.backspace();  updateHeader(); });
        setButton(28, numpadKey("0"),                            () -> { numpad.digit(0);     updateHeader(); });
        setButton(29, item(Material.BARRIER, "&f.",      10017), () -> { numpad.doubleZero(); updateHeader(); });

        setButton(33, item(Material.BARRIER, "&7戻る",   10001), () -> BankMainUI.openAsync(plugin, player));
        setButton(35, item(Material.BARRIER, "&a&l確定", 10005), this::doWithdraw);

        updateHeader();
    }

    private void updateHeader() {
        ItemStack header = item(Material.CYAN_STAINED_GLASS_PANE,
                "&b残高: &f" + config.format(balance) + "  &b入力: &f" + config.format(numpad.getInternal()));
        for (int i = 3; i <= 8; i++) inventory.setItem(i, header);
    }

    private void doWithdraw() {
        long internal = numpad.getInternal();
        if (internal <= 0) { player.sendActionBar(c("&c金額を入力してください")); return; }

        int needed = estimateItemCount(internal);
        int free = countFreeSlots(player);
        if (needed > free) { player.sendActionBar(c("&cインベントリの空きが不足しています")); return; }

        setButton(35, item(Material.BARRIER, "", 10000), null);

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var repo = plugin.getRepository();
                if (repo.isFrozen(uuid)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendActionBar(c("&c口座が凍結されています"));
                        restoreConfirmButton();
                    });
                    return;
                }
                long currentBalance = repo.getBalance(uuid);
                if (currentBalance < internal) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendActionBar(c("&c残高が不足しています"));
                        restoreConfirmButton();
                    });
                    return;
                }
                repo.addBalance(uuid, -internal);
                repo.updateLastTxnAt(uuid);

                List<ItemStack> items = CashItem.makeChange(internal,
                        config.getDenominations(), config.getServerKey());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (ItemStack it : items) {
                        player.getInventory().addItem(it).forEach((k, v) ->
                                player.getWorld().dropItemNaturally(player.getLocation(), v));
                    }
                    BankMainUI.openAsync(plugin, player);
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[BankWithdrawUI] withdraw failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendActionBar(c("&cエラーが発生しました"));
                    restoreConfirmButton();
                });
            }
        });
    }

    private void restoreConfirmButton() {
        setButton(35, item(Material.BARRIER, "&a&l確定", 10005), this::doWithdraw);
    }

    private static ItemStack numpadKey(String label) {
        int digit = Integer.parseInt(label);
        return item(Material.BARRIER, "&f" + label, 10007 + digit);
    }

    private int estimateItemCount(long internal) {
        int count = 0;
        long remaining = internal;
        for (EconomyConfig.Denomination d : config.getDenominations()) {
            long dInternal = (long) d.amount() * EconomyConfig.MULTIPLIER;
            long stacks = remaining / dInternal / 64;
            count += (int) stacks;
            if (remaining / dInternal % 64 > 0) count++;
            remaining %= dInternal;
        }
        return count + 1;
    }

    private static int countFreeSlots(Player player) {
        int free = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it == null) free++;
        }
        return free;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
            BankMainUI.openAsync(plugin, (Player) event.getPlayer());
        }
    }
}
