package net.alcaris.plugin.economy.crypto.engine;

import net.alcaris.plugin.economy.crypto.CryptoAsset;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VolatilityTracker {

    private final int lookback;
    private final Map<String, Deque<Double>> changeHistory = new ConcurrentHashMap<>();

    public VolatilityTracker(int lookback) {
        this.lookback = lookback;
    }

    public double update(CryptoAsset asset, double changeRate) {
        String symbol = asset.getSymbol();
        Deque<Double> history = changeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        history.addLast(Math.abs(changeRate));
        while (history.size() > lookback) history.pollFirst();

        double avgVol = history.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        asset.setRealizedVol(avgVol);

        double baseVol = asset.getVolatility();
        double clusteringFactor = 2.0;
        double dynVol = baseVol * (1.0 + avgVol * clusteringFactor);
        dynVol = Math.max(baseVol * 0.3, Math.min(baseVol * 3.0, dynVol));
        return dynVol;
    }
}
