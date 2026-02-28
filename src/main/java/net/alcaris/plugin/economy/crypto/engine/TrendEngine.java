package net.alcaris.plugin.economy.crypto.engine;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.crypto.CryptoAsset;

import java.util.Random;

public class TrendEngine {

    private final double bullBias;
    private final double bearBias;
    private final double reversalChance;
    private final double neutralReversal;
    private final long   maxDurationMs;
    private final double reversalShock;

    public TrendEngine(EconomyConfig config) {
        this.bullBias = config.getTrendBullBias();
        this.bearBias = config.getTrendBearBias();
        this.reversalChance = config.getTrendReversalChance();
        this.neutralReversal = config.getTrendNeutralReversal();
        this.maxDurationMs = config.getTrendMaxDurationMs();
        this.reversalShock = config.getTrendReversalShock();
    }

    public double evaluate(CryptoAsset asset, Random random) {
        CryptoAsset.TrendState state = asset.getTrendState();
        long elapsed = System.currentTimeMillis() - asset.getTrendStartedAt();
        boolean forceReversal = elapsed > maxDurationMs;

        double bias = 0.0;
        boolean reversed = false;

        switch (state) {
            case BULL -> {
                if (forceReversal || random.nextDouble() < reversalChance) {
                    asset.setTrendState(CryptoAsset.TrendState.BEAR);
                    asset.setTrendStartedAt(System.currentTimeMillis());
                    bias = -(bearBias - 0.5);
                    reversed = true;
                } else {
                    bias = bullBias - 0.5;
                }
            }
            case BEAR -> {
                if (forceReversal || random.nextDouble() < reversalChance) {
                    asset.setTrendState(CryptoAsset.TrendState.BULL);
                    asset.setTrendStartedAt(System.currentTimeMillis());
                    bias = bullBias - 0.5;
                    reversed = true;
                } else {
                    bias = -(bearBias - 0.5);
                }
            }
            case NEUTRAL -> {
                double roll = random.nextDouble();
                if (roll < neutralReversal / 2) {
                    asset.setTrendState(CryptoAsset.TrendState.BULL);
                    asset.setTrendStartedAt(System.currentTimeMillis());
                    bias = bullBias - 0.5;
                } else if (roll < neutralReversal) {
                    asset.setTrendState(CryptoAsset.TrendState.BEAR);
                    asset.setTrendStartedAt(System.currentTimeMillis());
                    bias = -(bearBias - 0.5);
                }
            }
        }

        return reversed ? bias * reversalShock : bias;
    }
}
