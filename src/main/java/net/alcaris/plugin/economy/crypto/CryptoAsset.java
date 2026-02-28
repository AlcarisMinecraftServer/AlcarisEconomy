package net.alcaris.plugin.economy.crypto;

import java.util.concurrent.atomic.AtomicLong;

public class CryptoAsset {

    public enum TrendState  { BULL, BEAR, NEUTRAL }
    public enum BubblePhase { NORMAL, BUBBLE, CRASH, RECOVERY }

    private final String symbol;
    private final String displayName;
    private final double volatility;
    private final long maxRate;
    private final long minRate;
    private final long initialRate;

    private volatile long currentRate;
    private final AtomicLong pendingNetBuy = new AtomicLong(0);
    private volatile double eventCorrection = 0.0;

    private volatile TrendState  trendState     = TrendState.NEUTRAL;
    private volatile long        trendStartedAt = System.currentTimeMillis();
    private volatile BubblePhase bubblePhase    = BubblePhase.NORMAL;
    private volatile double      realizedVol    = 0.0;
    private volatile long        buyPrice       = 0L;
    private volatile long        sellPrice      = 0L;

    public CryptoAsset(String symbol, String displayName, long initialRate,
                       double volatility, long maxRate, long minRate) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.initialRate = initialRate;
        this.currentRate = initialRate;
        this.volatility = volatility;
        this.maxRate = maxRate;
        this.minRate = minRate;
        this.buyPrice = initialRate;
        this.sellPrice = initialRate;
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
    public TrendState  getTrendState()               { return trendState; }
    public void        setTrendState(TrendState s)   { this.trendState = s; }
    public long        getTrendStartedAt()           { return trendStartedAt; }
    public void        setTrendStartedAt(long t)     { this.trendStartedAt = t; }
    public BubblePhase getBubblePhase()              { return bubblePhase; }
    public void        setBubblePhase(BubblePhase p) { this.bubblePhase = p; }
    public double      getRealizedVol()              { return realizedVol; }
    public void        setRealizedVol(double v)      { this.realizedVol = v; }
    public long        getBuyPrice()                 { return buyPrice; }
    public long        getSellPrice()                { return sellPrice; }
    public void        setBuyPrice(long v)           { this.buyPrice = v; }
    public void        setSellPrice(long v)          { this.sellPrice = v; }
}
