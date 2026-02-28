package net.alcaris.plugin.economy.crypto;

import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.crypto.engine.BubbleDetector;
import net.alcaris.plugin.economy.crypto.engine.EconomyIndexFetcher;
import net.alcaris.plugin.economy.crypto.engine.ImpactCalculator;
import net.alcaris.plugin.economy.crypto.engine.SpreadCalculator;
import net.alcaris.plugin.economy.crypto.engine.SpilloverEngine;
import net.alcaris.plugin.economy.crypto.engine.TrendEngine;
import net.alcaris.plugin.economy.crypto.engine.VolatilityTracker;

import java.util.Collection;
import java.util.Random;

public class RateEngine {

    private final Random              random = new Random();
    private final TrendEngine         trendEngine;
    private final BubbleDetector      bubbleDetector;
    private final ImpactCalculator    impactCalculator;
    private final SpreadCalculator    spreadCalculator;
    private final SpilloverEngine     spilloverEngine;
    private final EconomyIndexFetcher economyIndexFetcher;
    private final VolatilityTracker   volatilityTracker;

    public RateEngine(EconomyConfig config, DatabaseManager dbManager) {
        this.trendEngine = new TrendEngine(config);
        this.bubbleDetector = new BubbleDetector(config);
        this.impactCalculator = new ImpactCalculator(config);
        this.spreadCalculator = new SpreadCalculator(config);
        this.spilloverEngine = new SpilloverEngine(config);
        this.economyIndexFetcher = new EconomyIndexFetcher(dbManager, config);
        this.volatilityTracker = new VolatilityTracker(10);
    }

    public EconomyIndexFetcher getEconomyIndexFetcher() {
        return economyIndexFetcher;
    }

    public SpreadCalculator getSpreadCalculator() {
        return spreadCalculator;
    }

    public long tick(CryptoAsset asset, long netBuy, long totalSupply,
                     Collection<CryptoAsset> allAssets, double econCorrection) {

        double prevRate = asset.getCurrentRate();
        double prevChange = prevRate > 0 ? (prevRate - asset.getInitialRate()) / prevRate : 0.0;

        double dynVol = volatilityTracker.update(asset, prevChange);
        double trendCorrection = trendEngine.evaluate(asset, random);

        double sign = random.nextDouble() < 0.5 + trendCorrection ? 1.0 : -1.0;
        double randomChange = sign * random.nextDouble() * dynVol;

        double impactChange = impactCalculator.calculate(asset, netBuy, totalSupply);

        bubbleDetector.record(asset, asset.getCurrentRate());
        double crashOverride = bubbleDetector.evaluate(asset, random);

        double totalChange = crashOverride != 0.0
                ? crashOverride
                : randomChange + impactChange + econCorrection;

        long newMid = Math.round(asset.getCurrentRate() * (1.0 + totalChange));
        newMid = Math.max(asset.getMinRate(), Math.min(asset.getMaxRate(), newMid));

        SpreadCalculator.Spread spread = spreadCalculator.calculate(asset, newMid);
        asset.setBuyPrice(spread.buyPrice());
        asset.setSellPrice(spread.sellPrice());

        double decayed = asset.getEventCorrection() * 0.5;
        asset.setEventCorrection(Math.abs(decayed) < 0.001 ? 0.0 : decayed);

        if (Math.abs(totalChange) > spilloverEngine.getThreshold()) {
            spilloverEngine.propagate(asset, allAssets, totalChange);
        }

        return newMid;
    }
}
