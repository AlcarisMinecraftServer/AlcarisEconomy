package net.alcaris.plugin.economy.crypto.engine;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.crypto.CryptoAsset;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpreadCalculator {

    private final double baseSpread;
    private final double minSpread;
    private final double maxSpread;
    private final Map<String, Deque<Long>> volumeHistory = new ConcurrentHashMap<>();
    private static final int VOLUME_LOOKBACK = 20;

    public record Spread(long buyPrice, long sellPrice) {}

    public SpreadCalculator(EconomyConfig config) {
        this.baseSpread = config.getSpreadBase();
        this.minSpread = config.getSpreadMin();
        this.maxSpread = config.getSpreadMax();
    }

    public Spread calculate(CryptoAsset asset, long midPrice) {
        String symbol = asset.getSymbol();
        Deque<Long> history = volumeHistory.getOrDefault(symbol, new ArrayDeque<>());

        double liquidity = 1.0;
        if (!history.isEmpty()) {
            long sum = 0;
            for (long v : history) sum += v;
            long avg = sum / history.size();
            long last = history.peekLast();
            if (avg > 0) liquidity = (double) last / avg;
        }

        double spread = baseSpread / Math.max(0.1, liquidity);
        spread = Math.max(minSpread, Math.min(maxSpread, spread));

        long halfSpread = Math.round(midPrice * spread / 2.0);
        long buyPrice = midPrice + halfSpread;
        long sellPrice = Math.max(1, midPrice - halfSpread);
        return new Spread(buyPrice, sellPrice);
    }

    public void recordVolume(String symbol, long volume) {
        Deque<Long> history = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        history.addLast(volume);
        while (history.size() > VOLUME_LOOKBACK) history.pollFirst();
    }
}
