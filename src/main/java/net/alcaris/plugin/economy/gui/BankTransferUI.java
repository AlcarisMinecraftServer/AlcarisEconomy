package net.alcaris.plugin.economy.gui;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.bank.TransferManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.UUID;

public class BankTransferUI extends AbstractBankUI {

    private static final String TITLE = "§6§l振込";

    private final Player player;
    private final OfflinePlayer target;
    private final EconomyConfig config;
    private final long balance;
    private final long atmFee;
    private final BankNumpadHelper numpad;

    private BankTransferUI(AlcarisEconomy plugin, Player player, OfflinePlayer target,
                           long balance, long atmFee) {
        super(plugin, TITLE, 4, null);
        this.player = player;
        this.target = target;
        this.config = plugin.getEconomyConfig();
        this.balance = balance;
        this.atmFee = atmFee;
        long maxYen = Math.max(0, (balance - atmFee) / EconomyConfig.MULTIPLIER);
        this.numpad = new BankNumpadHelper(maxYen);
        buildLayout();
        updateHeader();
    }


    public static void openAsync(AlcarisEconomy plugin, Player player, OfflinePlayer target) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long balance = plugin.getRepository().getBalance(uuid);
                long atmFee = plugin.getEconomyConfig().getAtmFlat();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BankTransferUI ui = new BankTransferUI(plugin, player, target, balance, atmFee);
                    player.openInventory(ui.getInventory());
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("[BankTransferUI] openAsync failed: " + e.getMessage());
            }
        });
    }

    private void buildLayout() {
        setButton(9,  numpadKey("7"), () -> { numpad.digit(7);   updateHeader(); });
        setButton(10, numpadKey("8"), () -> { numpad.digit(8);   updateHeader(); });
        setButton(11, numpadKey("9"), () -> { numpad.digit(9);   updateHeader(); });
        setButton(13, presetKey("+1K"),   () -> { numpad.preset(1_000);   updateHeader(); });
        setButton(14, presetKey("+10K"),  () -> { numpad.preset(10_000);  updateHeader(); });
        setButton(15, presetKey("+100K"), () -> { numpad.preset(100_000); updateHeader(); });

        setButton(18, numpadKey("4"), () -> { numpad.digit(4); updateHeader(); });
        setButton(19, numpadKey("5"), () -> { numpad.digit(5); updateHeader(); });
        setButton(20, numpadKey("6"), () -> { numpad.digit(6); updateHeader(); });
        setButton(22, item(Material.ORANGE_STAINED_GLASS_PANE, "&6全額"), () -> { numpad.setAll(); updateHeader(); });

        setButton(27, item(Material.RED_STAINED_GLASS_PANE, "&cC"), () -> { numpad.backspace(); updateHeader(); });
        setButton(28, numpadKey("0"),  () -> { numpad.digit(0);    updateHeader(); });
        setButton(29, numpadKey("00"), () -> { numpad.doubleZero(); updateHeader(); });
        setButton(32, item(Material.GRAY_STAINED_GLASS_PANE, "&7戻る"), () -> BankMainUI.openAsync(plugin, player));
        setButton(34, item(Material.RED_STAINED_GLASS_PANE, "&cCLEAR"), () -> { numpad.clear(); updateHeader(); });
        setButton(35, item(Material.LIME_STAINED_GLASS_PANE, "&a&l送金"), this::doTransfer);
    }

    private void updateHeader() {
        String targetName = target.getName() != null ? target.getName() : "不明";
        ItemStack header = item(Material.CYAN_STAINED_GLASS_PANE,
                "&b振込先: &f" + targetName
                        + "  &b残高: &f" + config.format(balance)
                        + "  &b手数料: &f" + config.format(atmFee)
                        + "  &b入力: &f" + config.format(numpad.getInternal()));
        for (int i = 0; i < 9; i++) inventory.setItem(i, header);
    }

    private void showHeaderError(String msg) {
        ItemStack header = item(Material.RED_STAINED_GLASS_PANE, "&c" + msg);
        for (int i = 0; i < 9; i++) inventory.setItem(i, header);
    }

    private void doTransfer() {
        long internal = numpad.getInternal();
        if (internal <= 0) { showHeaderError("金額を入力してください"); return; }

        setButton(35, makeFiller(Material.GRAY_STAINED_GLASS_PANE), null);

        UUID fromUuid = player.getUniqueId();
        UUID toUuid = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TransferManager.TransferResult result = plugin.getTransferManager()
                    .transfer(fromUuid, toUuid, internal, TransferManager.TransferType.ATM_TRANSFER);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.success()) {
                    String name = target.getName() != null ? target.getName() : "不明";
                    player.sendMessage(c("&a&f" + name + " &aに &f" + config.format(result.amount())
                            + " &aを送金しました。手数料: &f" + config.format(result.fee())));
                    BankMainUI.openAsync(plugin, player);
                } else {
                    String reason = result.failReason();
                    if ("SENDER_FROZEN".equals(reason)) {
                        showHeaderError("送金者の口座が凍結されています");
                    } else if (reason != null && reason.startsWith("RECEIVER_FROZEN")) {
                        showHeaderError("受取人の口座が凍結されています");
                    } else if (reason != null && reason.startsWith("INSUFFICIENT")) {
                        showHeaderError("残高が不足しています（手数料込み）");
                    } else {
                        showHeaderError("送金に失敗しました");
                    }
                    setButton(35, item(Material.LIME_STAINED_GLASS_PANE, "&a&l送金"), this::doTransfer);
                }
            });
        });
    }

    private static ItemStack numpadKey(String label) {
        return item(Material.GRAY_STAINED_GLASS_PANE, "&f" + label);
    }

    private static ItemStack presetKey(String label) {
        return item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7" + label);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
            BankMainUI.openAsync(plugin, (Player) event.getPlayer());
        }
    }
}
