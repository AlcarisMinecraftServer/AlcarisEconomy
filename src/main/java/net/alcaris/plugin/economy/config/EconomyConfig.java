package net.alcaris.plugin.economy.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EconomyConfig {

    public static final long MULTIPLIER = 100L;

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

    public EconomyConfig(FileConfiguration cfg) {
        this.cfg = cfg;

        this.defaultBalance = (long) (cfg.getInt("currency.defaultBalance", 10000) * MULTIPLIER);
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
        this.remoteFlat = (long) (cfg.getInt("transfer.remote.flat", 50) * MULTIPLIER);
        this.remoteMinimum = (long) (cfg.getInt("transfer.remote.minimum", 50) * MULTIPLIER);
        this.remoteMaximum = (long) (cfg.getInt("transfer.remote.maximum", 5000) * MULTIPLIER);
        this.atmFlat = (long) (cfg.getInt("transfer.atm.flat", 100) * MULTIPLIER);

        this.inactiveDays = cfg.getInt("freeze.inactive_days", 30);
        this.warningDaysBefore = cfg.getInt("freeze.warning_days_before", 7);
        this.unfreezeFee = (long) (cfg.getInt("freeze.unfreeze_fee", 500) * MULTIPLIER);

        this.interestEnabled = cfg.getBoolean("interest.enabled", true);
        this.baseRate = cfg.getDouble("interest.base_rate", 0.005);
        this.maxInterestAmount = (long) (cfg.getInt("interest.max_amount", 10000) * MULTIPLIER);
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
            long initialRate = (long) mapInt(map, "initial_rate", 1000) * MULTIPLIER;
            double volatility = mapDbl(map);
            long maxRate = (long) mapInt(map, "max_rate", 10000) * MULTIPLIER;
            long minRate = (long) mapInt(map, "min_rate", 100) * MULTIPLIER;
            cryptoAssets.add(new CryptoAssetConfig(symbol, displayName, initialRate, volatility, maxRate, minRate));
        }
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

    public FileConfiguration getCfg() {
        return cfg;
    }

    public record Denomination(int amount, Material material, int customModelData,
                               String displayName, List<String> lore, boolean glint) {}

    public record TreasuryInit(String key, String description) {}

    public record CryptoAssetConfig(String symbol, String displayName, long initialRate,
                                    double volatility, long maxRate, long minRate) {}

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

    public String getTreasuryRouting(String type) {
        return switch (type) {
            case "transfer_fee" -> routingTransferFee;
            case "freeze_fee"   -> routingFreezeFee;
            case "crypto_fee"   -> routingCryptoFee;
            default -> "general";
        };
    }

    public String format(long internalAmount) {
        return formatStatic(internalAmount, singularMajor);
    }

    public static String formatStatic(long internalAmount) {
        return formatStatic(internalAmount, "円");
    }

    public static String formatStatic(long internalAmount, String unit) {
        long yen = internalAmount / MULTIPLIER;
        long frac = Math.abs(internalAmount % MULTIPLIER);
        if (frac == 0) {
            return String.format("%,d%s", yen, unit);
        }
        return String.format("%,d.%02d%s", yen, frac, unit);
    }
}
