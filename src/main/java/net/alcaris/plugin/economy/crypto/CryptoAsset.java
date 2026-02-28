package net.alcaris.plugin.economy.crypto;

import java.util.concurrent.atomic.AtomicLong;

public class CryptoAsset {

    private final String symbol;
    private final String displayName;
    private final double volatility;
    private final long maxRate;
    private final long minRate;
    private final long initialRate;

    private volatile long currentRate;
    private final AtomicLong pendingNetBuy = new AtomicLong(0);
    private volatile double eventCorrection = 0.0;

    public CryptoAsset(String symbol, String displayName, long initialRate,
                       double volatility, long maxRate, long minRate) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.initialRate = initialRate;
        this.currentRate = initialRate;
        this.volatility = volatility;
        this.maxRate = maxRate;
        this.minRate = minRate;
    }

    public void recordBuy(long amount)  { pendingNetBuy.addAndGet(amount); }
    public void recordSell(long amount) { pendingNetBuy.addAndGet(-amount); }
    public long consumeNetBuy()         { return pendingNetBuy.getAndSet(0); }

    public String getSymbol()      { return symbol; }
    public String getDisplayName() { return displayName; }
    public double getVolatility()  { return volatility; }
    public long getMaxRate()       { return maxRate; }
    public long getMinRate()       { return minRate; }
    public long getInitialRate()   { return initialRate; }
    public long getCurrentRate()   { return currentRate; }
    public void setCurrentRate(long rate)        { this.currentRate = rate; }
    public double getEventCorrection()           { return eventCorrection; }
    public void setEventCorrection(double v)     { this.eventCorrection = v; }
}
