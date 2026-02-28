package net.alcaris.plugin.economy.crypto.engine;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.crypto.CryptoAsset;
import org.bukkit.Bukkit;

public class ImpactCalculator {

    private final double  coefficient;
    private final double  immediateThreshold;
    private final double  largeThreshold;
    private final boolean largeAnnounce;

    public ImpactCalculator(EconomyConfig config) {
        this.coefficient = config.getImpactCoefficient();
        this.immediateThreshold = config.getImpactImmediateThreshold();
        this.largeThreshold = config.getImpactLargeThreshold();
        this.largeAnnounce = config.isImpactLargeAnnounce();
    }

    public double calculate(CryptoAsset asset, long netBuyVolume, long totalSupply) {
        if (netBuyVolume == 0 || totalSupply <= 0) return 0.0;

        double ratio = Math.abs((double) netBuyVolume / totalSupply);
        double sign = netBuyVolume > 0 ? 1.0 : -1.0;

        if (ratio >= largeThreshold) {
            if (largeAnnounce) {
                String direction = netBuyVolume > 0 ? "大量買い" : "大量売り";
                Bukkit.broadcastMessage("\u00a7e[仮想通貨] \u00a7f" + asset.getSymbol() + " に" + direction + "が入りました。");
            }
            return sign * ratio * coefficient;
        } else if (ratio >= immediateThreshold) {
            return sign * ratio * coefficient * 0.5;
        }
        return 0.0;
    }
}
