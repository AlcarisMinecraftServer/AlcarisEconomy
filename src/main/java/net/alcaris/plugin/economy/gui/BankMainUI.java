package net.alcaris.plugin.economy.gui;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.config.MessageConfig;
import net.alcaris.plugin.economy.repository.TxLogRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BankMainUI extends AbstractBankUI {

    private static final String TITLE = "§6§l銀行メニュー";

    private final Player player;
    private final boolean frozen;
    private final long balance;
    private final long lastTxnAt;
    private final FreezeManager.FreezeReason freezeReason;
    private final List<TxLogRepository.TxLogEntry> txLogs;
    private final Map<UUID, String> nameCache;
    private final EconomyConfig config;

    private BankMainUI(AlcarisEconomy plugin,
                       Player player,
                       boolean frozen,
                       long balance,
                       long lastTxnAt,
                       FreezeManager.FreezeReason freezeReason,
                       List<TxLogRepository.TxLogEntry> txLogs,
                       Map<UUID, String> nameCache) {
        super(plugin, TITLE, 4, null);
        this.player = player;
        this.frozen = frozen;
        this.balance = balance;
        this.lastTxnAt = lastTxnAt;
        this.freezeReason = freezeReason;
        this.txLogs = txLogs;
        this.nameCache = nameCache;
        this.config = plugin.getEconomyConfig();
        buildLayout();
    }

    @Override
    protected Component createTitle(String baseTitle) {
        return GuiTextures.createBankTitle("銀行メニュー");
    }

    public static void openAsync(AlcarisEconomy plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var repo = plugin.getRepository();
                var freezeManager = plugin.getFreezeManager();
                var txLog = plugin.getTxLogRepository();

                if (!repo.hasAccount(uuid)) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(c(MessageConfig.NO_ACCOUNT)));
                    return;
                }

                boolean frozen = repo.isFrozen(uuid);
                long balance = repo.getBalance(uuid);
                long lastTxnAt = repo.getLastTxnAt(uuid);
                FreezeManager.FreezeReason reason = frozen
                        ? freezeManager.getPublicFreezeReason(uuid) : null;
                List<TxLogRepository.TxLogEntry> logs = txLog.findByUuid(uuid, 5, 0);

                Map<UUID, String> nameCache = new HashMap<>();
                for (TxLogRepository.TxLogEntry entry : logs) {
                    cachePlayerName(entry.fromUuid(), nameCache);
                    cachePlayerName(entry.toUuid(), nameCache);
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    BankMainUI ui = new BankMainUI(plugin, player, frozen, balance,
                            lastTxnAt, reason, logs, nameCache);
                    player.openInventory(ui.getInventory());
                });

            } catch (SQLException e) {
                plugin.getLogger().warning("[BankMainUI] openAsync failed: " + e.getMessage());
            }
        });
    }

    private static void cachePlayerName(UUID uuid, Map<UUID, String> cache) {
        if (uuid == null || cache.containsKey(uuid)) return;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) cache.put(uuid, op.getName());
    }

    private void buildLayout() {

        setButton(10,  item(Material.CYAN_STAINED_GLASS_PANE,  "&b&l出金", -1),  this::openWithdraw);
        setButton(11,  item(Material.BARRIER, "", 10002));
        setButton(12, item(Material.LIME_STAINED_GLASS_PANE,  "&a&l入金", -1),  this::openDeposit);
        setButton(13,  item(Material.BARRIER, "", 10002));
        setButton(14, item(Material.ORANGE_STAINED_GLASS_PANE, "&6&l振込", -1), this::openTransfer);
        setButton(15,  item(Material.BARRIER, "", 10002));
        buildFreezeButton();

        buildAccountInfoButton();
        setButton(31, item(Material.RED_STAINED_GLASS_PANE, "&c&l閉じる", -1), player::closeInventory);
        buildTxLogButton();
    }

    private void buildFreezeButton() {
        if (frozen) {
            setButton(16, item(Material.RED_STAINED_GLASS_PANE, "&c&l凍結解除",
                    List.of("&7クリックで凍結を解除します")), this::doUnfreeze);
        } else {
            setButton(16, item(Material.GRAY_STAINED_GLASS_PANE, "&7凍結解除",
                    List.of("&7口座は凍結されていません")));
        }
    }

    private void buildAccountInfoButton() {
        String statusStr = buildStatusStr();
        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(lastTxnAt));
        List<String> lore = List.of(
                "&7残高: &f" + config.format(balance),
                "&7状態: " + statusStr,
                "&7最終取引: &f" + timeStr
        );
        ItemStack infoItem = item(Material.PURPLE_STAINED_GLASS_PANE, "&d&lアカウント情報", lore);
        setButton(30, infoItem, () -> {
            player.sendMessage(c("&d&lアカウント情報"));
            player.sendMessage(c("&7残高: &f" + config.format(balance)));
            player.sendMessage(c("&7状態: " + statusStr));
            player.sendMessage(c("&7最終取引: &f" + timeStr));
        });
    }

    private void buildTxLogButton() {
        List<String> lore = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        UUID uuid = player.getUniqueId();
        for (TxLogRepository.TxLogEntry entry : txLogs) {
            boolean isIncome = uuid.equals(entry.toUuid());
            String prefix = isIncome ? "&a+" : "&c-";
            String typeLabel = formatTxType(entry.type(), isIncome);
            String counterName = isIncome
                    ? nameCache.getOrDefault(entry.fromUuid(), "")
                    : nameCache.getOrDefault(entry.toUuid(), "");
            String dateStr = sdf.format(new Date(entry.createdAt()));
            lore.add(prefix + config.format(entry.amount())
                    + " &7" + typeLabel
                    + (counterName.isEmpty() ? "" : " " + counterName)
                    + " &8" + dateStr);
        }
        if (lore.isEmpty()) lore.add("&7取引履歴がありません");

        ItemStack txItem = item(Material.BLUE_STAINED_GLASS_PANE, "&b&l取引履歴", lore);
        List<String> loreCopy = List.copyOf(lore);
        setButton(32, txItem, () -> {
            player.sendMessage(c("&b&l取引履歴"));
            for (String line : loreCopy) player.sendMessage(c(line));
        });
    }

    private String buildStatusStr() {
        if (!frozen) return "&a通常";
        if (freezeReason == FreezeManager.FreezeReason.LOAN_OVERDUE)
            return "&c凍結 &7(ローン延滞)";
        return "&c凍結 &7(非活性)";
    }

    private static String formatTxType(String type, boolean isIncome) {
        if (type == null) return "不明";
        return switch (type) {
            case "ATM_TRANSFER" -> isIncome ? "振込受取" : "振込送金";
            case "REMOTE_PAY"   -> isIncome ? "送金受取" : "送金";
            case "INTEREST"     -> "利息付与";
            case "FREEZE_FEE"   -> "凍結解除手数料";
            case "CRYPTO"       -> "暗号資産";
            case "TREASURY_WITHDRAW" -> "国庫出金";
            case "CHEQUE_ISSUE" -> "小切手発行";
            case "CHEQUE_USE"   -> "小切手換金";
            case "LOAN_BORROW"  -> "ローン借入";
            case "LOAN_REPAY"   -> "ローン返済";
            case "LOAN_INTEREST"-> "ローン利息";
            default -> type;
        };
    }

    private void openWithdraw() {
        BankWithdrawUI.openAsync(plugin, player);
    }

    private void openDeposit() {
        BankDepositUI.openAsync(plugin, player);
    }

    private void openTransfer() {
        player.closeInventory();
        player.sendMessage(c("振込先のプレイヤー名を入力してください &8(cancelと入力することでキャンセルできます)"));
        registerTransferListener();
    }

    private void registerTransferListener() {
        AtomicBoolean handled = new AtomicBoolean(false);
        Listener[] ref = {null};
        ref[0] = new Listener() {
            @org.bukkit.event.EventHandler
            public void onChat(AsyncChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                if (!handled.compareAndSet(false, true)) return;
                event.setCancelled(true);
                HandlerList.unregisterAll(ref[0]);
                String name = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
                if (name.equalsIgnoreCase("cancel")) {
                    player.sendMessage(c("&c振込入力がキャンセルされました。"));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> resolveAndOpenTransfer(name));
            }
        };
        plugin.getServer().getPluginManager().registerEvents(ref[0], plugin);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (handled.compareAndSet(false, true)) {
                HandlerList.unregisterAll(ref[0]);
                player.sendMessage(c("&c振込入力がキャンセルされました。"));
            }
        }, 20L * 30);
    }

    private void resolveAndOpenTransfer(String name) {
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) target = Bukkit.getOfflinePlayer(name);
        if (target.getName() == null) {
            player.sendMessage(c("&cプレイヤーが見つかりません: " + name));
            openAsync(plugin, player);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(c("&c自分自身には振込できません。"));
            openAsync(plugin, player);
            return;
        }
        BankTransferUI.openAsync(plugin, player, target);
    }

    private void doUnfreeze() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = plugin.getFreezeManager().unfreeze(player.getUniqueId(), true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error == null) {
                    player.sendMessage(c("&a凍結を解除しました。手数料: &f"
                            + config.format(config.getUnfreezeFee())));
                } else if ("NOT_FROZEN".equals(error)) {
                    player.sendMessage(c("&c口座は凍結されていません。"));
                } else if ("LOAN_OVERDUE".equals(error)) {
                    player.sendMessage(c("&cローン延滞による凍結は /loan repay で返済しないと解除できません。"));
                } else if (error.startsWith("INSUFFICIENT")) {
                    player.sendMessage(c("&c残高不足で凍結解除できません。手数料: &f"
                            + config.format(config.getUnfreezeFee())));
                } else {
                    player.sendMessage(c("&c凍結解除に失敗しました: " + error));
                }
                openAsync(plugin, player);
            });
        });
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {}
}
