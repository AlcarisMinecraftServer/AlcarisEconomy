package net.alcaris.plugin.economy.crypto.engine;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.crypto.CryptoAsset;

import java.util.Collection;

public class SpilloverEngine {

    private final boolean enabled;
    private final double  ratio;
    private final double  threshold;

    public SpilloverEngine(EconomyConfig config) {
        this.enabled = config.isSpilloverEnabled();
        this.ratio = config.getSpilloverRatio();
        this.threshold = config.getSpilloverThreshold();
    }

    public double getThreshold() { return threshold; }

    public void propagate(CryptoAsset changed, Collection<CryptoAsset> allAssets, double changeRate) {
        if (!enabled) return;
        double spillover = changeRate * ratio;
        for (CryptoAsset other : allAssets) {
            if (other.getSymbol().equals(changed.getSymbol())) continue;
            long newRate = Math.round(other.getCurrentRate() * (1.0 + spillover));
            newRate = Math.max(other.getMinRate(), Math.min(other.getMaxRate(), newRate));
            other.setCurrentRate(newRate);
        }
    }
}
