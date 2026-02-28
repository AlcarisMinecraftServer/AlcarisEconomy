package net.alcaris.plugin.economy;

import net.alcaris.plugin.core.AlcarisCore;
import net.alcaris.plugin.core.database.DatabaseManager;
import net.alcaris.plugin.economy.bank.FreezeManager;
import net.alcaris.plugin.economy.bank.TransferManager;
import net.alcaris.plugin.economy.bank.TreasuryManager;
import net.alcaris.plugin.economy.bank.TreasuryRepository;
import net.alcaris.plugin.economy.command.AdminCommand;
import net.alcaris.plugin.economy.command.BankCommand;
import net.alcaris.plugin.economy.command.ChequeCommand;
import net.alcaris.plugin.economy.command.CryptoCommand;
import net.alcaris.plugin.economy.command.EconomyCommand;
import net.alcaris.plugin.economy.command.LoanCommand;
import net.alcaris.plugin.economy.command.MoneyCommand;
import net.alcaris.plugin.economy.command.TreasuryCommand;
import net.alcaris.plugin.economy.command.TxLogCommand;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.crypto.CryptoMarket;
import net.alcaris.plugin.economy.currency.CashItem;
import net.alcaris.plugin.economy.currency.CashItemListener;
import net.alcaris.plugin.economy.currency.ChequeItem;
import net.alcaris.plugin.economy.currency.ChequeListener;
import net.alcaris.plugin.economy.currency.ChequeService;
import net.alcaris.plugin.economy.database.SchemaInitializer;
import net.alcaris.plugin.economy.interest.ActivityTracker;
import net.alcaris.plugin.economy.interest.InterestScheduler;
import net.alcaris.plugin.economy.loan.CollateralManager;
import net.alcaris.plugin.economy.loan.CollateralUI;
import net.alcaris.plugin.economy.loan.LoanNoteItem;
import net.alcaris.plugin.economy.loan.LoanNoteListener;
import net.alcaris.plugin.economy.loan.LoanScheduler;
import net.alcaris.plugin.economy.loan.PlayerLoanRepository;
import net.alcaris.plugin.economy.loan.PlayerLoanService;
import net.alcaris.plugin.economy.loan.ServerLoanRepository;
import net.alcaris.plugin.economy.loan.ServerLoanService;
import net.alcaris.plugin.economy.repository.BalanceRepository;
import net.alcaris.plugin.economy.repository.ChequeRepository;
import net.alcaris.plugin.economy.repository.LazyRepository;
import net.alcaris.plugin.economy.repository.SyncRepository;
import net.alcaris.plugin.economy.repository.TxLogRepository;
import net.alcaris.plugin.economy.util.BalanceProviderRegistry;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class AlcarisEconomy extends JavaPlugin {

    private DatabaseManager dbManager;
    private EconomyConfig economyConfig;
    private BalanceRepository repository;
    private TreasuryRepository treasuryRepository;
    private TreasuryManager treasuryManager;
    private FreezeManager freezeManager;
    private TransferManager transferManager;
    private ActivityTracker activityTracker;
    private InterestScheduler interestScheduler;
    private CryptoMarket cryptoMarket;
    private VaultEconomy vaultEconomy;
    private AdminCommand adminCommand;
    private ChequeService chequeService;
    private ChequeRepository chequeRepository;
    private TxLogRepository txLogRepository;
    private PlayerLoanService playerLoanService;
    private ServerLoanService serverLoanService;
    private LoanScheduler loanScheduler;

    private volatile boolean initialized = false;

    @Override
    public void onEnable() {
        try {
            AlcarisCore core = (AlcarisCore) getServer().getPluginManager().getPlugin("AlcarisCore");
            if (core == null || !core.isInitialized()) {
                shutdownWithError("AlcarisCore is not loaded or not yet initialized.");
                return;
            }
            this.dbManager = core.getDatabaseManager();
            getLogger().info("Connected to AlcarisCore DatabaseManager.");

            saveDefaultConfig();
            this.economyConfig = new EconomyConfig(getConfig());
            getLogger().info("Economy config loaded.");

            new SchemaInitializer(dbManager, getLogger()).initialize();

            if (economyConfig.isLazyWrite()) {
                LazyRepository lazyRepo = new LazyRepository(dbManager, this);
                lazyRepo.startFlushTask();
                this.repository = lazyRepo;
                getLogger().info("Using LazyRepository (async flush every 30s).");
            } else {
                this.repository = new SyncRepository(dbManager);
                getLogger().info("Using SyncRepository (immediate writes).");
            }

            this.treasuryRepository = new TreasuryRepository(dbManager);
            this.treasuryManager = new TreasuryManager(treasuryRepository, economyConfig, repository, getLogger());
            this.treasuryManager.initializeDefaults();

            this.freezeManager = new FreezeManager(repository, treasuryManager, economyConfig, dbManager, this);
            this.transferManager = new TransferManager(repository, treasuryManager, economyConfig, dbManager, getLogger());

            this.activityTracker = new ActivityTracker(economyConfig, dbManager, this);
            this.activityTracker.loadCurrentWeek();
            this.interestScheduler = new InterestScheduler(repository, activityTracker, economyConfig, dbManager, this);

            if (economyConfig.isCryptoEnabled()) {
                this.cryptoMarket = new CryptoMarket(repository, treasuryManager, economyConfig, dbManager, this);
                this.cryptoMarket.initialize();
                getLogger().info("CryptoMarket initialized.");
            } else {
                getLogger().info("CryptoMarket disabled.");
            }

            if (!hookVault()) {
                shutdownWithError("Vault plugin not found or service registration failed.");
                return;
            }

            CashItem.initialize(this);
            ChequeItem.initialize(this);
            LoanNoteItem.initialize(this);
            if (economyConfig.getChequeCustomModelData() != 0)
                net.alcaris.plugin.economy.currency.ChequeItem.setCustomModelData(economyConfig.getChequeCustomModelData());
            if (economyConfig.getLoanNoteCustomModelData() != 0)
                LoanNoteItem.setCustomModelData(economyConfig.getLoanNoteCustomModelData());

            this.chequeRepository = new ChequeRepository(dbManager);
            this.txLogRepository = new TxLogRepository(dbManager);
            this.chequeService = new ChequeService(repository, transferManager, chequeRepository, economyConfig, getLogger());

            PlayerLoanRepository playerLoanRepo = new PlayerLoanRepository(dbManager);
            ServerLoanRepository serverLoanRepo = new ServerLoanRepository(dbManager);
            CollateralManager collateralManager = new CollateralManager(this, playerLoanRepo);
            this.playerLoanService = new PlayerLoanService(repository, playerLoanRepo, collateralManager, economyConfig, this);
            this.serverLoanService = new ServerLoanService(repository, serverLoanRepo, treasuryManager, transferManager, freezeManager, economyConfig);
            this.loanScheduler = new LoanScheduler(repository, playerLoanRepo, serverLoanRepo, treasuryManager, freezeManager, economyConfig, this);
            interestScheduler.setServerLoanRepo(serverLoanRepo);

            registerBalanceProviders();

            MoneyCommand moneyCommand = new MoneyCommand(this);
            getCommand("money").setExecutor(moneyCommand);
            getCommand("money").setTabCompleter(moneyCommand);
            getCommand("pay").setExecutor(moneyCommand);
            getCommand("pay").setTabCompleter(moneyCommand);

            BankCommand bankCommand = new BankCommand(this);
            getCommand("bank").setExecutor(bankCommand);
            getCommand("bank").setTabCompleter(bankCommand);

            this.adminCommand = new AdminCommand(this);
            EconomyCommand economyCommand = new EconomyCommand(adminCommand);
            getCommand("economy").setExecutor(economyCommand);
            getCommand("economy").setTabCompleter(economyCommand);

            TreasuryCommand treasuryCommand = new TreasuryCommand(this);
            getCommand("treasury").setExecutor(treasuryCommand);
            getCommand("treasury").setTabCompleter(treasuryCommand);

            if (economyConfig.isCryptoEnabled()) {
                CryptoCommand cryptoCommand = new CryptoCommand(this);
                getCommand("crypto").setExecutor(cryptoCommand);
                getCommand("crypto").setTabCompleter(cryptoCommand);
            }

            if (economyConfig.isChequeEnabled()) {
                ChequeCommand chequeCommand = new ChequeCommand(this, chequeService, chequeRepository);
                getCommand("cheque").setExecutor(chequeCommand);
                getCommand("cheque").setTabCompleter(chequeCommand);
                getServer().getPluginManager().registerEvents(new ChequeListener(this, chequeService), this);
            }

            TxLogCommand txLogCommand = new TxLogCommand(this, txLogRepository);
            getCommand("txlog").setExecutor(txLogCommand);
            getCommand("txlog").setTabCompleter(txLogCommand);

            if (economyConfig.isLoanPlayerEnabled() || economyConfig.isLoanServerEnabled()) {
                LoanCommand loanCommand = new LoanCommand(this, playerLoanService, serverLoanService);
                getCommand("loan").setExecutor(loanCommand);
                getCommand("loan").setTabCompleter(loanCommand);
                CollateralUI collateralUI = new CollateralUI(this, collateralManager);
                getServer().getPluginManager().registerEvents(collateralUI, this);
                if (economyConfig.isLoanPlayerEnabled()) {
                    getServer().getPluginManager().registerEvents(new LoanNoteListener(this, playerLoanService), this);
                }
                loanScheduler.start();
            }

            getServer().getPluginManager().registerEvents(new EventListener(this), this);
            getServer().getPluginManager().registerEvents(new CashItemListener(this), this);

            freezeManager.startScheduler();
            freezeManager.runStartupCheck();
            if (economyConfig.isInterestEnabled()) {
                interestScheduler.start();
            }

            this.initialized = true;
            getLogger().info("AlcarisEconomy enabled successfully.");

        } catch (SQLException e) {
            shutdownWithError("Database error during startup: " + e.getMessage());
        } catch (Exception e) {
            shutdownWithError("Fatal error during startup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (repository != null) {
            repository.flush();
            repository.shutdown();
            getLogger().info("Repository flushed and shut down.");
        }
        if (vaultEconomy != null) {
            getServer().getServicesManager().unregister(Economy.class, vaultEconomy);
        }
        getLogger().info("AlcarisEconomy disabled.");
    }

    private void registerBalanceProviders() {
        BalanceProviderRegistry.register("cash", 5, player -> {
            long cash = net.alcaris.plugin.economy.currency.CashItem.countInventoryCash(player, economyConfig);
            return java.util.concurrent.CompletableFuture.completedFuture(
                    cash > 0 ? "&7現金: &f" + economyConfig.format(cash) : null);
        });
        BalanceProviderRegistry.register("account", 10, player ->
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        if (!repository.hasAccount(player.getUniqueId())) return null;
                        long balance = repository.getBalance(player.getUniqueId());
                        return "&7口座残高: &f" + economyConfig.format(balance);
                    } catch (java.sql.SQLException e) {
                        return null;
                    }
                }));
        if (economyConfig.isCryptoEnabled() && cryptoMarket != null) {
            BalanceProviderRegistry.register("crypto", 20, player ->
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            cryptoMarket.buildHoldingLine(player)));
        }
    }

    private boolean hookVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found!");
            return false;
        }
        this.vaultEconomy = new VaultEconomy(this);
        getServer().getServicesManager().register(Economy.class, vaultEconomy, this, ServicePriority.Highest);
        getLogger().info("Vault economy service registered.");
        return true;
    }

    private void shutdownWithError(String reason) {
        getLogger().severe("========================================");
        getLogger().severe("FATAL ERROR: " + reason);
        getLogger().severe("Disabling AlcarisEconomy...");
        getLogger().severe("========================================");
        getServer().getPluginManager().disablePlugin(this);
    }

    public EconomyConfig getEconomyConfig()             { return economyConfig; }
    public DatabaseManager getDbManager()               { return dbManager; }
    public BalanceRepository getRepository()            { return repository; }
    public TreasuryManager getTreasuryManager()         { return treasuryManager; }
    public FreezeManager getFreezeManager()             { return freezeManager; }
    public TransferManager getTransferManager()         { return transferManager; }
    public ActivityTracker getActivityTracker()         { return activityTracker; }
    public InterestScheduler getInterestScheduler()     { return interestScheduler; }
    public CryptoMarket getCryptoMarket()               { return cryptoMarket; }
    public AdminCommand getAdminCommand()               { return adminCommand; }
    public ChequeService getChequeService()             { return chequeService; }
    public ChequeRepository getChequeRepository()       { return chequeRepository; }
    public TxLogRepository getTxLogRepository()         { return txLogRepository; }
    public PlayerLoanService getPlayerLoanService()     { return playerLoanService; }
    public ServerLoanService getServerLoanService()     { return serverLoanService; }
    public boolean isInitialized()                      { return initialized; }
}
