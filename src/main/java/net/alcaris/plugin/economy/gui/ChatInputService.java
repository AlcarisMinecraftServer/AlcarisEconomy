package net.alcaris.plugin.economy.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.alcaris.plugin.economy.AlcarisEconomy;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputService implements Listener {

    private static final long DEFAULT_TIMEOUT_TICKS = 20L * 30;

    private final AlcarisEconomy plugin;
    private final ConcurrentHashMap<UUID, PendingRequest> pending = new ConcurrentHashMap<>();

    public ChatInputService(AlcarisEconomy plugin) {
        this.plugin = plugin;
    }

    public void requestInput(Player player, Consumer<String> onInput, Runnable onTimeout) {
        requestInput(player, onInput, onTimeout, DEFAULT_TIMEOUT_TICKS);
    }

    public void requestInput(Player player, Consumer<String> onInput, Runnable onTimeout, long timeoutTicks) {
        UUID uuid = player.getUniqueId();
        PendingRequest previous = pending.remove(uuid);
        if (previous != null) previous.cancelTimeout();

        PendingRequest request = new PendingRequest(onInput, onTimeout);
        pending.put(uuid, request);
        request.timeoutTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingRequest current = pending.remove(uuid);
            if (current == request && current.onTimeout != null) current.onTimeout.run();
        }, timeoutTicks).getTaskId();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        PendingRequest request = pending.remove(event.getPlayer().getUniqueId());
        if (request == null) return;
        request.cancelTimeout();
        event.setCancelled(true);
        event.viewers().clear();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> request.onInput.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PendingRequest request = pending.remove(event.getPlayer().getUniqueId());
        if (request != null) request.cancelTimeout();
    }

    private static final class PendingRequest {
        private final Consumer<String> onInput;
        private final Runnable onTimeout;
        private int timeoutTaskId = -1;

        PendingRequest(Consumer<String> onInput, Runnable onTimeout) {
            this.onInput = onInput;
            this.onTimeout = onTimeout;
        }

        void cancelTimeout() {
            if (timeoutTaskId != -1) Bukkit.getScheduler().cancelTask(timeoutTaskId);
        }
    }
}
