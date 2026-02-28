package net.alcaris.plugin.economy.util;

import org.bukkit.entity.Player;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceProviderRegistry {

    public interface Provider {
        CompletableFuture<String> line(Player player);
    }

    private static final ConcurrentHashMap<String,
            AbstractMap.SimpleEntry<Integer, Provider>> providers = new ConcurrentHashMap<>();

    public static void register(String id, int order, Provider provider) {
        providers.put(id, new AbstractMap.SimpleEntry<>(order, provider));
    }

    public static CompletableFuture<List<String>> buildLines(Player player) {
        List<CompletableFuture<String>> futures = providers.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().getKey()))
                .map(e -> e.getValue().getValue().line(player).exceptionally(ex -> null))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(s -> s != null && !s.isBlank())
                        .toList());
    }
}
