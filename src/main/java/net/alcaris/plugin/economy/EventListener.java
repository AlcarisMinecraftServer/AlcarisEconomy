package net.alcaris.plugin.economy;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.interest.ActivityTracker;
import net.alcaris.plugin.economy.interest.ActivityType;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

public class EventListener implements Listener {

    private final AlcarisEconomy plugin;
    private final BalanceRepository repository;
    private final EconomyConfig config;
    private final FreezeManager freezeManager;
    private final ActivityTracker activityTracker;
    private final Logger logger;

    public EventListener(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.repository = plugin.getRepository();
        this.config = plugin.getEconomyConfig();
        this.freezeManager = plugin.getFreezeManager();
        this.activityTracker = plugin.getActivityTracker();
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (config.isCreateAccountOnJoin() && !repository.hasAccount(uuid)) {
                    repository.createAccount(uuid, config.getDefaultBalance());
                    logger.info("[Economy] Created account for " + player.getName());
                }
                if (repository.hasAccount(uuid)) {
                    freezeManager.notifyOnLogin(uuid, player);
                }
            } catch (SQLException e) {
                logger.warning("[EventListener] onJoin failed for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {}

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) return;

        activityTracker.addScore(killer.getUniqueId(), ActivityType.COMBAT, config.getCombatPerKill());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();

        if (config.getMiningBlocks().contains(type)) {
            activityTracker.addScore(player.getUniqueId(), ActivityType.MINING, config.getMiningPerBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (activityTracker.checkChatCooldown(player.getUniqueId(), message)) {
            activityTracker.addScore(player.getUniqueId(), ActivityType.CHAT, config.getChatPerMessage());
        }
    }
}
