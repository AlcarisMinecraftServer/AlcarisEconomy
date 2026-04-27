package net.alcaris.plugin.economy;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

public class VaultEconomy implements Economy {

    private final AlcarisEconomy plugin;
    private final BalanceRepository repository;
    private final EconomyConfig config;
    private final Logger logger;

    public VaultEconomy(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.repository = plugin.getRepository();
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
    }

    private static long toInternal(double amount) {
        return Math.round(amount * EconomyConfig.MULTIPLIER);
    }

    private static double toExternal(long amount) {
        return (double) amount / EconomyConfig.MULTIPLIER;
    }

    @Override public boolean isEnabled()             { return plugin.isEnabled(); }
    @Override public String getName()                { return "AlcarisEconomy"; }
    @Override public boolean hasBankSupport()        { return false; }
    @Override public int fractionalDigits()          { return 2; }
    @Override public String format(double amount)    { return config.format(toInternal(amount)); }
    @Override public String currencyNamePlural()     { return config.getSingularMajor(); }
    @Override public String currencyNameSingular()   { return config.getSingularMajor(); }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(plugin.getServer().getOfflinePlayer(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        try {
            return repository.hasAccount(player.getUniqueId());
        } catch (SQLException e) {
            logger.warning("[VaultEconomy] hasAccount failed: " + e.getMessage());
            return false;
        }
    }

    @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }

    @Override
    public double getBalance(String playerName) {
        return getBalance(plugin.getServer().getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        try {
            return toExternal(repository.getBalance(player.getUniqueId()));
        } catch (SQLException e) {
            logger.warning("[VaultEconomy] getBalance failed: " + e.getMessage());
            return 0.0;
        }
    }

    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(String playerName, double amount)                      { return has(plugin.getServer().getOfflinePlayer(playerName), amount); }
    @Override public boolean has(OfflinePlayer player, double amount)                   { return !isFrozenSafe(player) && getBalance(player) >= amount; }
    @Override public boolean has(String playerName, String worldName, double amount)    { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

    private boolean isFrozenSafe(OfflinePlayer player) {
        try {
            return repository.isFrozen(player.getUniqueId());
        } catch (SQLException e) {
            logger.warning("[VaultEconomy] isFrozen check failed for " + player.getUniqueId() + ": " + e.getMessage());
            return true;
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(plugin.getServer().getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Amount must be positive");
        }
        try {
            if (repository.isFrozen(player.getUniqueId())) {
                return new EconomyResponse(amount, toExternal(repository.getBalance(player.getUniqueId())),
                        EconomyResponse.ResponseType.FAILURE, "Account is frozen");
            }
            long internal = toInternal(amount);
            long balance = repository.getBalance(player.getUniqueId());
            if (balance < internal) {
                return new EconomyResponse(amount, toExternal(balance),
                        EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
            }
            repository.addBalance(player.getUniqueId(), -internal);
            long newBalance = repository.getBalance(player.getUniqueId());
            return new EconomyResponse(amount, toExternal(newBalance), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (SQLException e) {
            logger.warning("[VaultEconomy] withdrawPlayer failed: " + e.getMessage());
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, e.getMessage());
        }
    }

    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(plugin.getServer().getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, "Amount must be positive");
        }
        try {
            if (repository.isFrozen(player.getUniqueId())) {
                return new EconomyResponse(amount, toExternal(repository.getBalance(player.getUniqueId())),
                        EconomyResponse.ResponseType.FAILURE, "Account is frozen");
            }
            long internal = toInternal(amount);
            repository.addBalance(player.getUniqueId(), internal);
            long newBalance = repository.getBalance(player.getUniqueId());
            return new EconomyResponse(amount, toExternal(newBalance), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (SQLException e) {
            logger.warning("[VaultEconomy] depositPlayer failed: " + e.getMessage());
            return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.FAILURE, e.getMessage());
        }
    }

    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(plugin.getServer().getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        try {
            if (repository.hasAccount(player.getUniqueId())) return false;
            repository.createAccount(player.getUniqueId(), config.getDefaultBalance());
            return true;
        } catch (SQLException e) {
            logger.warning("[VaultEconomy] createPlayerAccount failed: " + e.getMessage());
            return false;
        }
    }

    @Override public boolean createPlayerAccount(String playerName, String worldName) { return createPlayerAccount(playerName); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return createPlayerAccount(player); }

    private static final EconomyResponse NOT_SUPPORTED =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");

    @Override public EconomyResponse createBank(String name, String player)         { return NOT_SUPPORTED; }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player)  { return NOT_SUPPORTED; }
    @Override public EconomyResponse deleteBank(String name)                        { return NOT_SUPPORTED; }
    @Override public EconomyResponse bankBalance(String name)                       { return NOT_SUPPORTED; }
    @Override public EconomyResponse bankHas(String name, double amount)            { return NOT_SUPPORTED; }
    @Override public EconomyResponse bankWithdraw(String name, double amount)       { return NOT_SUPPORTED; }
    @Override public EconomyResponse bankDeposit(String name, double amount)        { return NOT_SUPPORTED; }
    @Override public EconomyResponse isBankOwner(String name, String playerName)    { return NOT_SUPPORTED; }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return NOT_SUPPORTED; }
    @Override public EconomyResponse isBankMember(String name, String playerName)   { return NOT_SUPPORTED; }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player){ return NOT_SUPPORTED; }
    @Override public List<String> getBanks()                                        { return List.of(); }
}
