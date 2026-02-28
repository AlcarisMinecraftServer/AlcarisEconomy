package net.alcaris.plugin.economy.crypto.engine;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.crypto.CryptoAsset;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class BubbleDetector {

    private final Map<String, Deque<Long>> rateHistory = new ConcurrentHashMap<>();
    private final int    lookbackTicks;
    private final double overheatThreshold;
    private final double overcoolThreshold;
    private final double crashFirstDropMin;
    private final double crashFirstDropMax;
    private final int    aftershockCount;
    private final long   recoveryMsMin;
    private final long   recoveryMsMax;
    private final Map<String, Integer> aftershockRemaining = new ConcurrentHashMap<>();
    private final Map<String, Long> recoveryEndAt = new ConcurrentHashMap<>();

    public BubbleDetector(EconomyConfig config) {
        this.lookbackTicks = config.getBubbleLookbackTicks();
        this.overheatThreshold = config.getBubbleOverheatThreshold();
        this.overcoolThreshold = config.getBubbleOvercoolThreshold();
        this.crashFirstDropMin = config.getBubbleCrashFirstDropMin();
        this.crashFirstDropMax = config.getBubbleCrashFirstDropMax();
        this.aftershockCount = config.getBubbleAftershockCount();
        this.recoveryMsMin = config.getBubbleRecoveryMsMin();
        this.recoveryMsMax = config.getBubbleRecoveryMsMax();
    }

    public void record(CryptoAsset asset, long rate) {
        String symbol = asset.getSymbol();
        Deque<Long> history = rateHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        history.addLast(rate);
        while (history.size() > lookbackTicks) history.pollFirst();
    }

    public double evaluate(CryptoAsset asset, Random random) {
        String symbol = asset.getSymbol();
        CryptoAsset.BubblePhase phase = asset.getBubblePhase();

        if (phase == CryptoAsset.BubblePhase.RECOVERY) {
            Long end = recoveryEndAt.get(symbol);
            if (end != null && System.currentTimeMillis() >= end) {
                asset.setBubblePhase(CryptoAsset.BubblePhase.NORMAL);
                recoveryEndAt.remove(symbol);
            }
            return 0.0;
        }

        if (phase == CryptoAsset.BubblePhase.CRASH) {
            int remaining = aftershockRemaining.getOrDefault(symbol, 0);
            if (remaining > 0) {
                aftershockRemaining.put(symbol, remaining - 1);
                return -(crashFirstDropMin + random.nextDouble() * (crashFirstDropMax - crashFirstDropMin)) * 0.5;
            } else {
                long recoveryMs = recoveryMsMin + (long)(random.nextDouble() * (recoveryMsMax - recoveryMsMin));
                recoveryEndAt.put(symbol, System.currentTimeMillis() + recoveryMs);
                asset.setBubblePhase(CryptoAsset.BubblePhase.RECOVERY);
                return 0.0;
            }
        }

        Deque<Long> history = rateHistory.get(symbol);
        if (history == null || history.size() < lookbackTicks) return 0.0;

        long first = history.peekFirst();
        long last = history.peekLast();
        if (first <= 0) return 0.0;

        double change = (double)(last - first) / first;

        if (phase == CryptoAsset.BubblePhase.BUBBLE || change > overheatThreshold) {
            asset.setBubblePhase(CryptoAsset.BubblePhase.CRASH);
            aftershockRemaining.put(symbol, aftershockCount);
            double drop = -(crashFirstDropMin + random.nextDouble() * (crashFirstDropMax - crashFirstDropMin));
            return drop;
        }

        if (change < overcoolThreshold) {
            asset.setBubblePhase(CryptoAsset.BubblePhase.BUBBLE);
        }

        return 0.0;
    }
}
