package net.alcaris.plugin.economy.loan;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CollateralUI implements Listener {

    private static final int CONFIRM_SLOT = 8;
    private static final int GUI_SIZE = 9;

    private final JavaPlugin plugin;
    private final CollateralManager collateralManager;
    private final Logger logger;
    private final Map<UUID, Long> pendingLoanId = new ConcurrentHashMap<>();

    public CollateralUI(JavaPlugin plugin, CollateralManager collateralManager) {
        this.plugin = plugin;
        this.collateralManager = collateralManager;
        this.logger = plugin.getLogger();
    }

    public void open(Player player, long loanId) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                LegacyComponentSerializer.legacyAmpersand().deserialize("&6担保設定 &7(スロット0-7)"));
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = confirm.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&a&l確定"));
        confirm.setItemMeta(meta);
        inv.setItem(CONFIRM_SLOT, confirm);
        pendingLoanId.put(player.getUniqueId(), loanId);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!pendingLoanId.containsKey(player.getUniqueId())) return;
        if (!titleMatches(event.getView().title())) return;

        if (event.getRawSlot() == CONFIRM_SLOT) {
            event.setCancelled(true);
            Inventory inv = event.getInventory();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < CONFIRM_SLOT; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) items.add(item.clone());
            }
            long loanId = pendingLoanId.remove(player.getUniqueId());
            if (items.isEmpty()) { player.closeInventory(); return; }

            for (int i = 0; i < CONFIRM_SLOT; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null) player.getInventory().removeItem(item);
                inv.clear(i);
            }
            player.closeInventory();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    collateralManager.save(loanId, items);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(colorize("&a担保を設定しました。")));
                } catch (SQLException e) {
                    logger.warning("[CollateralUI] save failed: " + e.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(colorize("&c担保設定に失敗しました。")));
                }
            });
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            pendingLoanId.remove(player.getUniqueId());
        }
    }

    private boolean titleMatches(net.kyori.adventure.text.Component title) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);
        return plain.contains("担保設定");
    }

    private static String colorize(String msg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
    }
}
