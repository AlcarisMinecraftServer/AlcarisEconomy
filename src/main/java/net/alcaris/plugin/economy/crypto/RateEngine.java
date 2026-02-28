package net.alcaris.plugin.economy.crypto;

import java.util.Random;

public class RateEngine {

    private final Random random = new Random();

    public long computeNewRate(CryptoAsset asset, long netBuyVolume) {
        double current = asset.getCurrentRate();

        double randomFactor = 1.0 + (random.nextDouble() * 2 - 1) * asset.getVolatility();

        double demandRatio = netBuyVolume == 0 ? 0.0
                : Math.signum(netBuyVolume) * 0.05 * Math.min(1.0,
                Math.abs((double) netBuyVolume) / 1_000_000.0);
        double demandFactor = 1.0 + demandRatio;

        double eventFactor = 1.0 + asset.getEventCorrection();

        long newRate = Math.round(current * randomFactor * demandFactor * eventFactor);
        newRate = Math.max(asset.getMinRate(), Math.min(asset.getMaxRate(), newRate));

        double decayed = asset.getEventCorrection() * 0.5;
        if (Math.abs(decayed) < 0.001) decayed = 0.0;
        asset.setEventCorrection(decayed);

        return newRate;
    }
}
