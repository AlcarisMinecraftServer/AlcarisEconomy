package net.alcaris.plugin.economy.currency;

import net.alcaris.plugin.economy.config.EconomyConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ChequeListener implements Listener {

    private final JavaPlugin plugin;
    private final ChequeService chequeService;

    public ChequeListener(JavaPlugin plugin, ChequeService chequeService) {
        this.plugin = plugin;
        this.chequeService = chequeService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) return;
        if (!ChequeItem.hasChequeMarker(item)) return;

        long chequeId = ChequeItem.getChequeId(item);
        if (chequeId < 0) return;

        event.setCancelled(true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String err = chequeService.use(event.getPlayer(), chequeId);
            long amount = 0L;
            try { amount = chequeService.getAmount(chequeId); } catch (Exception ignored) {}
            final long finalAmount = amount;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (err == null) {
                    event.getPlayer().sendMessage(colorize("&a小切手を換金しました: &f" +
                            EconomyConfig.formatStatic(finalAmount)));
                    ItemStack used = ChequeItem.createUsed(chequeId, finalAmount);
                    event.getPlayer().getInventory().addItem(used).forEach((k, v) ->
                            event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), v));
                    item.setAmount(item.getAmount() - 1);
                } else if (!err.equals("COOLDOWN")) {
                    String msg = switch (err) {
                        case "ALREADY_USED" -> "&c小切手は既に換金されています。";
                        case "NOT_FOUND"    -> "&c無効な小切手です。";
                        case "OWN_CHEQUE"   -> "&c自分が発行した小切手は換金できません。";
                        default             -> "&c換金に失敗しました: " + err;
                    };
                    event.getPlayer().sendMessage(colorize(msg));
                }
            });
        });
    }

    private static String colorize(String msg) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(msg));
    }
}
