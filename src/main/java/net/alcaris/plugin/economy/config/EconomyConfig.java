package net.alcaris.plugin.economy.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EconomyConfig {

    private final FileConfiguration cfg;

    private final long defaultBalance;
    private final boolean createAccountOnJoin;
    private final String currencyFormat;
    private final String singularMajor;

    private final String serverKey;
    private final List<Denomination> denominations;

    private final String routingTransferFee;
    private final String routingFreezeFee;
    private final String routingCryptoFee;
    private final List<TreasuryInit> initialTreasuries;

    private final boolean lazyWrite;

    private final double remoteRate;
    private final long remoteFlat;
    private final long remoteMinimum;
    private final long remoteMaximum;
    private final long atmFlat;

    private final int inactiveDays;
    private final int warningDaysBefore;
    private final long unfreezeFee;

    private final boolean interestEnabled;
    private final double baseRate;
    private final long maxInterestAmount;
    private final int applyHour;
    private final int combatPerKill;
    private final int combatWeeklyCap;
    private final int miningPerBlock;
    private final int miningWeeklyCap;
    private final int minigamePerJoin;
    private final int minigameWeeklyCap;
    private final int chatPerMessage;
    private final int chatWeeklyCap;
    private final int chatCooldownSeconds;
    private final int totalWeeklyCap;
    private final List<Material> miningBlocks;

    private final boolean cryptoEnabled;
    private final double cryptoFeeRate;
    private final int rateUpdateInterval;
    private final int historyRetentionHours;
    private final List<CryptoAssetConfig> cryptoAssets;

    private final boolean chequeEnabled;
    private final long    chequeMinAmount;
    private final long    chequeMaxAmount;
    private final int     chequeMaxNoteLength;
    private final int     chequeCustomModelData;

    private final boolean loanPlayerEnabled;
    private final int     loanMaxDurationDays;
    private final double  loanOverdueInterestRate;
    private final double  loanOverdueInterestCap;
    private final int     loanNoteCustomModelData;

    private final boolean loanServerEnabled;
    private final long    loanServerBorrowLimit;
    private final double  loanServerBorrowMultiplier;
    private final double  loanServerDailyRate;
    private final int     loanOverdue1Days;
    private final double  loanOverdue1Rate;
    private final int     loanOverdue2Days;
    private final double  loanOverdue2Rate;
    private final String  loanServerTreasuryKey;

    private final double trendBullBias;
    private final double trendBearBias;
    private final double trendReversalChance;
    private final double trendNeutralReversal;
    private final long   trendMaxDurationMs;
    private final double trendReversalShock;

    private final boolean bubbleEnabled;
    private final int     bubbleLookbackTicks;
    private final double  bubbleOverheatThreshold;
    private final double  bubbleOvercoolThreshold;
    private final double  bubbleCrashFirstDropMin;
    private final double  bubbleCrashFirstDropMax;
    private final int     bubbleAftershockCount;
    private final long    bubbleRecoveryMsMin;
    private final long    bubbleRecoveryMsMax;

    private final double  impactCoefficient;
    private final double  impactImmediateThreshold;
    private final double  impactLargeThreshold;
    private final boolean impactLargeAnnounce;

    private final double spreadBase;
    private final double spreadMin;
    private final double spreadMax;

    private final boolean spilloverEnabled;
    private final double  spilloverRatio;
    private final double  spilloverThreshold;

    private final boolean econIndexEnabled;
    private final double  econIndexMaxCorrection;
    private final double  econIndexTransferWeight;
    private final double  econIndexOnlineWeight;
    private final double  econIndexFreezeWeight;
    private final long    econIndexTreasuryThreshold;
    private final double  econIndexTreasuryFactor;

    public EconomyConfig(FileConfiguration cfg) {
        this.cfg = cfg;

        this.defaultBalance = cfg.getInt("currency.defaultBalance", 10000);
        this.createAccountOnJoin = cfg.getBoolean("currency.createAccountOnJoin", true);
        this.currencyFormat = cfg.getString("currency.format.format", "{major}円");
        this.singularMajor = cfg.getString("currency.format.singularMajor", "円");

        this.serverKey = cfg.getString("cash_item.server_key", "CHANGE_THIS");
        this.denominations = new ArrayList<>();
        List<java.util.Map<?, ?>> denomList = cfg.getMapList("cash_item.denominations");
        for (java.util.Map<?, ?> rawMap : denomList) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) rawMap;
            int amount = mapInt(map, "amount", 100);
            Material material = Material.getMaterial(mapStr(map, "material", "PAPER"));
            if (material == null) material = Material.PAPER;
            int cmd = mapInt(map, "custom_model_data", 0);
            String displayName = mapStr(map, "display_name", "&f" + amount + "円");
            @SuppressWarnings("unchecked")
            List<String> lore = map.containsKey("lore") ? (List<String>) map.get("lore") : new ArrayList<>();
            boolean glint = Boolean.parseBoolean(mapStr(map, "glint", "false"));
            denominations.add(new Denomination(amount, material, cmd, displayName, lore, glint));
        }
        denominations.sort((a, b) -> Integer.compare(b.amount(), a.amount()));

        this.routingTransferFee = cfg.getString("treasury.routing.transfer_fee", "general");
        this.routingFreezeFee = cfg.getString("treasury.routing.freeze_fee", "general");
        this.routingCryptoFee = cfg.getString("treasury.routing.crypto_fee", "general");
        this.initialTreasuries = new ArrayList<>();
        List<java.util.Map<?, ?>> treasuryList = cfg.getMapList("treasury.initial");
        for (java.util.Map<?, ?> rawMap : treasuryList) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) rawMap;
            String key = mapStr(map, "key", "general");
            String desc = mapStr(map, "description", "");
            initialTreasuries.add(new TreasuryInit(key, desc));
        }

        this.lazyWrite = cfg.getBoolean("lazyWrite", true);

        this.remoteRate = cfg.getDouble("transfer.remote.rate", 0.03);
        this.remoteFlat = cfg.getInt("transfer.remote.flat", 50);
        this.remoteMinimum = cfg.getInt("transfer.remote.minimum", 50);
        this.remoteMaximum = cfg.getInt("transfer.remote.maximum", 5000);
        this.atmFlat = cfg.getInt("transfer.atm.flat", 100);

        this.inactiveDays = cfg.getInt("freeze.inactive_days", 30);
        this.warningDaysBefore = cfg.getInt("freeze.warning_days_before", 7);
        this.unfreezeFee = cfg.getInt("freeze.unfreeze_fee", 500);

        this.interestEnabled = cfg.getBoolean("interest.enabled", true);
        this.baseRate = cfg.getDouble("interest.base_rate", 0.005);
        this.maxInterestAmount = cfg.getInt("interest.max_amount", 10000);
        this.applyHour = cfg.getInt("interest.apply_hour", 0);
        ConfigurationSection act = cfg.getConfigurationSection("interest.activity");
        this.combatPerKill = act != null ? act.getInt("combat_per_kill", 2) : 2;
        this.combatWeeklyCap = act != null ? act.getInt("combat_weekly_cap", 100) : 100;
        this.miningPerBlock = act != null ? act.getInt("mining_per_block", 1) : 1;
        this.miningWeeklyCap = act != null ? act.getInt("mining_weekly_cap", 200) : 200;
        this.minigamePerJoin = act != null ? act.getInt("minigame_per_join", 10) : 10;
        this.minigameWeeklyCap = act != null ? act.getInt("minigame_weekly_cap", 100) : 100;
        this.chatPerMessage = act != null ? act.getInt("chat_per_message", 1) : 1;
        this.chatWeeklyCap = act != null ? act.getInt("chat_weekly_cap", 50) : 50;
        this.chatCooldownSeconds = act != null ? act.getInt("chat_cooldown_seconds", 60) : 60;
        this.totalWeeklyCap = act != null ? act.getInt("total_weekly_cap", 300) : 300;
        this.miningBlocks = new ArrayList<>();
        List<String> blockNames = cfg.getStringList("interest.mining_blocks");
        for (String name : blockNames) {
            Material m = Material.getMaterial(name);
            if (m != null) miningBlocks.add(m);
        }

        this.cryptoEnabled = cfg.getBoolean("crypto.enabled", true);
        this.cryptoFeeRate = cfg.getDouble("crypto.fee_rate", 0.02);
        this.rateUpdateInterval = cfg.getInt("crypto.rate_update_interval", 3600);
        this.historyRetentionHours = cfg.getInt("crypto.history_retention_hours", 168);
        this.cryptoAssets = new ArrayList<>();
        List<java.util.Map<?, ?>> assetList = cfg.getMapList("crypto.assets");
        for (java.util.Map<?, ?> rawMap : assetList) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) rawMap;
            String symbol = mapStr(map, "symbol", "???");
            String displayName = mapStr(map, "display_name", symbol);
            long initialRate = mapInt(map, "initial_rate", 1000);
            double volatility = mapDbl(map);
            long maxRate = mapInt(map, "max_rate", 10000);
            long minRate = mapInt(map, "min_rate", 100);
            double clusteringFactor = mapDblKey(map, "clustering_factor", 2.0);
            int clusteringLookback = mapInt(map, "clustering_lookback", 10);
            cryptoAssets.add(new CryptoAssetConfig(symbol, displayName, initialRate, volatility, maxRate, minRate,
                    clusteringFactor, clusteringLookback));
        }

        this.chequeEnabled = cfg.getBoolean("cheque.enabled", true);
        this.chequeMinAmount = cfg.getInt("cheque.min_amount", 1);
        this.chequeMaxAmount = cfg.getInt("cheque.max_amount", 1000000);
        this.chequeMaxNoteLength = cfg.getInt("cheque.max_note_length", 50);
        this.chequeCustomModelData = cfg.getInt("cheque.custom_model_data", 2001);

        this.loanPlayerEnabled = cfg.getBoolean("loan.player.enabled", true);
        this.loanMaxDurationDays = cfg.getInt("loan.player.max_duration_days", 180);
        this.loanOverdueInterestRate = cfg.getDouble("loan.player.overdue_interest_rate", 0.005);
        this.loanOverdueInterestCap = cfg.getDouble("loan.player.overdue_interest_cap", 3.0);
        this.loanNoteCustomModelData = cfg.getInt("loan.player.note_custom_model_data", 3001);

        this.loanServerEnabled = cfg.getBoolean("loan.server.enabled", true);
        this.loanServerBorrowLimit = cfg.getInt("loan.server.borrow_limit", 500000);
        this.loanServerBorrowMultiplier = cfg.getDouble("loan.server.borrow_limit_multiplier", 2.0);
        this.loanServerDailyRate = cfg.getDouble("loan.server.daily_interest_rate", 0.003);
        this.loanOverdue1Days = cfg.getInt("loan.server.overdue_1_days", 7);
        this.loanOverdue1Rate = cfg.getDouble("loan.server.overdue_1_rate", 0.005);
        this.loanOverdue2Days = cfg.getInt("loan.server.overdue_2_days", 14);
        this.loanOverdue2Rate = cfg.getDouble("loan.server.overdue_2_rate", 0.010);
        this.loanServerTreasuryKey = cfg.getString("loan.server.treasury_key", "general");

        this.trendBullBias = cfg.getDouble("crypto.engine.trend.bull_bias", 0.62);
        this.trendBearBias = cfg.getDouble("crypto.engine.trend.bear_bias", 0.38);
        this.trendReversalChance = cfg.getDouble("crypto.engine.trend.reversal_chance", 0.05);
        this.trendNeutralReversal = cfg.getDouble("crypto.engine.trend.neutral_reversal", 0.10);
        this.trendMaxDurationMs = cfg.getInt("crypto.engine.trend.max_duration_hours", 72) * 3_600_000L;
        this.trendReversalShock = cfg.getDouble("crypto.engine.trend.reversal_shock", 1.75);

        this.bubbleEnabled = cfg.getBoolean("crypto.engine.bubble.enabled", true);
        this.bubbleLookbackTicks = cfg.getInt("crypto.engine.bubble.lookback_ticks", 30);
        this.bubbleOverheatThreshold = cfg.getDouble("crypto.engine.bubble.overheat_threshold", 1.50);
        this.bubbleOvercoolThreshold = cfg.getDouble("crypto.engine.bubble.overcool_threshold", -0.60);
        this.bubbleCrashFirstDropMin = cfg.getDouble("crypto.engine.bubble.crash_first_drop_min", 0.20);
        this.bubbleCrashFirstDropMax = cfg.getDouble("crypto.engine.bubble.crash_first_drop_max", 0.35);
        this.bubbleAftershockCount = cfg.getInt("crypto.engine.bubble.aftershock_count", 3);
        this.bubbleRecoveryMsMin = cfg.getInt("crypto.engine.bubble.recovery_hours_min", 6) * 3_600_000L;
        this.bubbleRecoveryMsMax = cfg.getInt("crypto.engine.bubble.recovery_hours_max", 24) * 3_600_000L;

        this.impactCoefficient = cfg.getDouble("crypto.engine.impact.coefficient", 0.3);
        this.impactImmediateThreshold = cfg.getDouble("crypto.engine.impact.immediate_threshold", 0.05);
        this.impactLargeThreshold = cfg.getDouble("crypto.engine.impact.large_threshold", 0.15);
        this.impactLargeAnnounce = cfg.getBoolean("crypto.engine.impact.large_trade_announce", true);

        this.spreadBase = cfg.getDouble("crypto.engine.spread.base", 0.02);
        this.spreadMin = cfg.getDouble("crypto.engine.spread.min", 0.005);
        this.spreadMax = cfg.getDouble("crypto.engine.spread.max", 0.10);

        this.spilloverEnabled = cfg.getBoolean("crypto.engine.spillover.enabled", true);
        this.spilloverRatio = cfg.getDouble("crypto.engine.spillover.ratio", 0.25);
        this.spilloverThreshold = cfg.getDouble("crypto.engine.spillover.threshold", 0.10);

        this.econIndexEnabled = cfg.getBoolean("crypto.engine.economy_index.enabled", true);
        this.econIndexMaxCorrection = cfg.getDouble("crypto.engine.economy_index.max_correction", 0.03);
        this.econIndexTransferWeight = cfg.getDouble("crypto.engine.economy_index.transfer_weight", 1.0);
        this.econIndexOnlineWeight = cfg.getDouble("crypto.engine.economy_index.online_weight", 0.5);
        this.econIndexFreezeWeight = cfg.getDouble("crypto.engine.economy_index.freeze_weight", 0.5);
        this.econIndexTreasuryThreshold = cfg.getInt("crypto.engine.economy_index.treasury_stability_threshold", 1000000);
        this.econIndexTreasuryFactor = cfg.getDouble("crypto.engine.economy_index.treasury_stability_factor", 0.9);
    }

    private static String mapStr(java.util.Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private static int mapInt(java.util.Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    private static double mapDbl(Map<String, Object> map) {
        Object v = map.get("volatility");
        if (v instanceof Number n) return n.doubleValue();
        return 0.03;
    }

    @SuppressWarnings("SameParameterValue")
    private static double mapDblKey(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    public FileConfiguration getCfg() {
        return cfg;
    }

    public record Denomination(int amount, Material material, int customModelData,
                               String displayName, List<String> lore, boolean glint) {}

    public record TreasuryInit(String key, String description) {}

    public record CryptoAssetConfig(String symbol, String displayName, long initialRate,
                                    double volatility, long maxRate, long minRate,
                                    double clusteringFactor, int clusteringLookback) {}

    public long getDefaultBalance()                 { return defaultBalance; }
    public boolean isCreateAccountOnJoin()          { return createAccountOnJoin; }
    public String getCurrencyFormat()               { return currencyFormat; }
    public String getSingularMajor()                { return singularMajor; }
    public String getServerKey()                    { return serverKey; }
    public List<Denomination> getDenominations()    { return denominations; }
    public String getRoutingTransferFee()           { return routingTransferFee; }
    public String getRoutingFreezeFee()             { return routingFreezeFee; }
    public String getRoutingCryptoFee()             { return routingCryptoFee; }
    public List<TreasuryInit> getInitialTreasuries(){ return initialTreasuries; }
    public boolean isLazyWrite()                    { return lazyWrite; }
    public double getRemoteRate()                   { return remoteRate; }
    public long getRemoteFlat()                     { return remoteFlat; }
    public long getRemoteMinimum()                  { return remoteMinimum; }
    public long getRemoteMaximum()                  { return remoteMaximum; }
    public long getAtmFlat()                        { return atmFlat; }
    public int getInactiveDays()                    { return inactiveDays; }
    public int getWarningDaysBefore()               { return warningDaysBefore; }
    public long getUnfreezeFee()                    { return unfreezeFee; }
    public boolean isInterestEnabled()              { return interestEnabled; }
    public double getBaseRate()                     { return baseRate; }
    public long getMaxInterestAmount()              { return maxInterestAmount; }
    public int getApplyHour()                       { return applyHour; }
    public int getCombatPerKill()                   { return combatPerKill; }
    public int getCombatWeeklyCap()                 { return combatWeeklyCap; }
    public int getMiningPerBlock()                  { return miningPerBlock; }
    public int getMiningWeeklyCap()                 { return miningWeeklyCap; }
    public int getMinigamePerJoin()                 { return minigamePerJoin; }
    public int getMinigameWeeklyCap()               { return minigameWeeklyCap; }
    public int getChatPerMessage()                  { return chatPerMessage; }
    public int getChatWeeklyCap()                   { return chatWeeklyCap; }
    public int getChatCooldownSeconds()             { return chatCooldownSeconds; }
    public int getTotalWeeklyCap()                  { return totalWeeklyCap; }
    public List<Material> getMiningBlocks()         { return miningBlocks; }
    public boolean isCryptoEnabled()                { return cryptoEnabled; }
    public double getCryptoFeeRate()                { return cryptoFeeRate; }
    public int getRateUpdateInterval()              { return rateUpdateInterval; }
    public int getHistoryRetentionHours()           { return historyRetentionHours; }
    public List<CryptoAssetConfig> getCryptoAssets(){ return cryptoAssets; }
    public boolean isChequeEnabled()               { return chequeEnabled; }
    public long getChequeMinAmount()               { return chequeMinAmount; }
    public long getChequeMaxAmount()               { return chequeMaxAmount; }
    public int getChequeMaxNoteLength()            { return chequeMaxNoteLength; }
    public int getChequeCustomModelData()          { return chequeCustomModelData; }
    public boolean isLoanPlayerEnabled()           { return loanPlayerEnabled; }
    public int getLoanMaxDurationDays()            { return loanMaxDurationDays; }
    public double getLoanOverdueInterestRate()     { return loanOverdueInterestRate; }
    public double getLoanOverdueInterestCap()      { return loanOverdueInterestCap; }
    public int getLoanNoteCustomModelData()        { return loanNoteCustomModelData; }
    public boolean isLoanServerEnabled()           { return loanServerEnabled; }
    public long getLoanServerBorrowLimit()         { return loanServerBorrowLimit; }
    public double getLoanServerBorrowMultiplier()  { return loanServerBorrowMultiplier; }
    public double getLoanServerDailyRate()         { return loanServerDailyRate; }
    public int getLoanOverdue1Days()               { return loanOverdue1Days; }
    public double getLoanOverdue1Rate()            { return loanOverdue1Rate; }
    public int getLoanOverdue2Days()               { return loanOverdue2Days; }
    public double getLoanOverdue2Rate()            { return loanOverdue2Rate; }
    public String getLoanServerTreasuryKey()       { return loanServerTreasuryKey; }
    public double getTrendBullBias()               { return trendBullBias; }
    public double getTrendBearBias()               { return trendBearBias; }
    public double getTrendReversalChance()         { return trendReversalChance; }
    public double getTrendNeutralReversal()        { return trendNeutralReversal; }
    public long getTrendMaxDurationMs()            { return trendMaxDurationMs; }
    public double getTrendReversalShock()          { return trendReversalShock; }
    public boolean isBubbleEnabled()               { return bubbleEnabled; }
    public int getBubbleLookbackTicks()            { return bubbleLookbackTicks; }
    public double getBubbleOverheatThreshold()     { return bubbleOverheatThreshold; }
    public double getBubbleOvercoolThreshold()     { return bubbleOvercoolThreshold; }
    public double getBubbleCrashFirstDropMin()     { return bubbleCrashFirstDropMin; }
    public double getBubbleCrashFirstDropMax()     { return bubbleCrashFirstDropMax; }
    public int getBubbleAftershockCount()          { return bubbleAftershockCount; }
    public long getBubbleRecoveryMsMin()           { return bubbleRecoveryMsMin; }
    public long getBubbleRecoveryMsMax()           { return bubbleRecoveryMsMax; }
    public double getImpactCoefficient()           { return impactCoefficient; }
    public double getImpactImmediateThreshold()    { return impactImmediateThreshold; }
    public double getImpactLargeThreshold()        { return impactLargeThreshold; }
    public boolean isImpactLargeAnnounce()         { return impactLargeAnnounce; }
    public double getSpreadBase()                  { return spreadBase; }
    public double getSpreadMin()                   { return spreadMin; }
    public double getSpreadMax()                   { return spreadMax; }
    public boolean isSpilloverEnabled()            { return spilloverEnabled; }
    public double getSpilloverRatio()              { return spilloverRatio; }
    public double getSpilloverThreshold()          { return spilloverThreshold; }
    public boolean isEconIndexEnabled()            { return econIndexEnabled; }
    public double getEconIndexMaxCorrection()      { return econIndexMaxCorrection; }
    public double getEconIndexTransferWeight()     { return econIndexTransferWeight; }
    public double getEconIndexOnlineWeight()       { return econIndexOnlineWeight; }
    public double getEconIndexFreezeWeight()       { return econIndexFreezeWeight; }
    public long getEconIndexTreasuryThreshold()    { return econIndexTreasuryThreshold; }
    public double getEconIndexTreasuryFactor()     { return econIndexTreasuryFactor; }

    public String getTreasuryRouting(String type) {
        return switch (type) {
            case "transfer_fee" -> routingTransferFee;
            case "freeze_fee"   -> routingFreezeFee;
            case "crypto_fee"   -> routingCryptoFee;
            default -> "general";
        };
    }

    public String format(long amount) {
        return formatStatic(amount, singularMajor);
    }

    public static String formatStatic(long amount) {
        return formatStatic(amount, "円");
    }

    public static String formatStatic(long amount, String unit) {
        return String.format("%,d%s", amount, unit);
    }
}
