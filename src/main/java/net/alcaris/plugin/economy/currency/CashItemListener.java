package net.alcaris.plugin.economy.currency;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.config.EconomyConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class CashItemListener implements Listener {

    private final AlcarisEconomy plugin;
    private final String serverKey;
    private final Set<Integer> validAmounts;

    public CashItemListener(AlcarisEconomy plugin) {
        this.plugin = plugin;
        EconomyConfig cfg = plugin.getEconomyConfig();
        this.serverKey = cfg.getServerKey();
        this.validAmounts = CashItem.buildValidAmounts(cfg.getDenominations());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!CashItem.hasCashMarker(item)) return;

        if (!CashItem.isValid(item, serverKey, validAmounts)) {
            event.setCancelled(true);
            event.getItem().remove();
            Player player = event.getPlayer();
            plugin.getLogger().warning("[CashItem] Forged cash item destroyed on pickup by " + player.getName());
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(net.alcaris.plugin.economy.config.MessageConfig.CASH_FORGED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack cursor = event.getCursor();
        if (CashItem.hasCashMarker(cursor) && !CashItem.isValid(cursor, serverKey, validAmounts)) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            plugin.getLogger().warning("[CashItem] Forged cash item removed from cursor of " + player.getName());
        }
    }
}
