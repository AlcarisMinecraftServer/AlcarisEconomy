package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.alcaris.plugin.economy.config.EconomyConfig;
import net.alcaris.plugin.economy.config.MessageConfig;
import net.alcaris.plugin.economy.crypto.CryptoAsset;
import net.alcaris.plugin.economy.crypto.CryptoMarket;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CryptoCommand implements CommandExecutor, TabCompleter {

    private static final long CRYPTO_MULTIPLIER = 100L;

    private final AlcarisEconomy plugin;
    private final CryptoMarket market;
    private final EconomyConfig config;
    private final Logger logger;

    public CryptoCommand(AlcarisEconomy plugin) {
        this.plugin = plugin;
        this.market = plugin.getCryptoMarket();
        this.config = plugin.getEconomyConfig();
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!config.isCryptoEnabled()) {
            CommandUtils.msg(sender, MessageConfig.CRYPTO_DISABLED);
            return true;
        }
        if (args.length == 0) return showHelp(sender);
        return switch (args[0].toLowerCase()) {
            case "list"      -> cmdList(sender);
            case "buy"       -> cmdBuy(sender, args);
            case "sell"      -> cmdSell(sender, args);
            case "portfolio" -> cmdPortfolio(sender);
            case "history"   -> cmdHistory(sender, args);
            case "admin"     -> cmdAdmin(sender, args);
            default -> showHelp(sender);
        };
    }

    private boolean cmdList(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6&l仮想通貨マーケット &8&m----");
        Collection<CryptoAsset> assets = market.getAllAssets();
        if (assets.isEmpty()) {
            CommandUtils.msg(sender, "&7設定された資産がありません。");
            return true;
        }
        for (CryptoAsset asset : assets) {
            CommandUtils.msg(sender, String.format("&e%-6s &7%s &8| &aRate: &f%s",
                    asset.getSymbol(), asset.getDisplayName(),
                    config.format(asset.getCurrentRate())));
        }
        return true;
    }

    private boolean cmdBuy(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 3) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/crypto buy <symbol> <amount>"));
            return true;
        }
        String symbol = args[1].toUpperCase();
        if (!market.hasAsset(symbol)) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_NOT_FOUND, "symbol", symbol));
            return true;
        }
        double qty = CommandUtils.parsePositiveDouble(args[2]);
        if (qty < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long quantityInternal = Math.round(qty * CRYPTO_MULTIPLIER);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CryptoMarket.TradeResult result = market.buy(player.getUniqueId(), symbol, quantityInternal);
            if (result.success()) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_BOUGHT,
                        "amount", String.format("%.2f", qty),
                        "symbol", symbol,
                        "cost", config.format(result.cost()),
                        "fee", config.format(result.fee())));
            } else {
                CommandUtils.msg(sender, "&c購入に失敗しました: " + result.failReason());
            }
        });
        return true;
    }

    private boolean cmdSell(CommandSender sender, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        if (args.length < 3) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/crypto sell <symbol> <amount>"));
            return true;
        }
        String symbol = args[1].toUpperCase();
        if (!market.hasAsset(symbol)) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_NOT_FOUND, "symbol", symbol));
            return true;
        }
        double qty = CommandUtils.parsePositiveDouble(args[2]);
        if (qty < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
        long quantityInternal = Math.round(qty * CRYPTO_MULTIPLIER);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CryptoMarket.TradeResult result = market.sell(player.getUniqueId(), symbol, quantityInternal);
            if (result.success()) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_SOLD,
                        "amount", String.format("%.2f", qty),
                        "symbol", symbol,
                        "received", config.format(result.cost() - result.fee()),
                        "fee", config.format(result.fee())));
            } else if (result.failReason().equals("INSUFFICIENT_HOLDING")) {
                CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_INSUFFICIENT, "symbol", symbol));
            } else {
                CommandUtils.msg(sender, "&c売却に失敗しました: " + result.failReason());
            }
        });
        return true;
    }

    private boolean cmdPortfolio(CommandSender sender) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) return true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<String, Long> portfolio = market.getPortfolio(player.getUniqueId());
                CommandUtils.msg(sender, "&8&m----&r &6&lポートフォリオ &8&m----");
                if (portfolio.isEmpty()) {
                    CommandUtils.msg(sender, "&7仮想通貨を保有していません。");
                    return;
                }
                for (Map.Entry<String, Long> entry : portfolio.entrySet()) {
                    CryptoAsset asset = market.getAsset(entry.getKey());
                    if (asset == null || entry.getValue() <= 0) continue;
                    double qty = (double) entry.getValue() / CRYPTO_MULTIPLIER;
                    long value = entry.getValue() * asset.getCurrentRate() / CRYPTO_MULTIPLIER;
                    CommandUtils.msg(sender, String.format("&e%-6s &f%.2f &7(≈ &a%s&7)",
                            entry.getKey(), qty, config.format(value)));
                }
            } catch (SQLException e) {
                logger.warning("[CryptoCommand] portfolio failed: " + e.getMessage());
            }
        });
        return true;
    }

    private boolean cmdHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.USAGE, "usage", "/crypto history <symbol>"));
            return true;
        }
        String symbol = args[1].toUpperCase();
        if (!market.hasAsset(symbol)) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_NOT_FOUND, "symbol", symbol));
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<long[]> history = market.getRateHistory(symbol, 10);
                CommandUtils.msg(sender, "&8&m----&r &6&l" + symbol + " レート履歴 &8&m----");
                if (history.isEmpty()) { CommandUtils.msg(sender, "&7履歴がありません。"); return; }
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
                for (long[] row : history) {
                    CommandUtils.msg(sender, "&7" + sdf.format(new Date(row[1])) + " &f→ &a" + config.format(row[0]));
                }
            } catch (SQLException e) {
                logger.warning("[CryptoCommand] history failed: " + e.getMessage());
            }
        });
        return true;
    }

    private boolean cmdAdmin(CommandSender sender, String[] args) {
        if (CommandUtils.checkPermission(sender, "alcariseconomy.crypto.admin")) return true;
        if (args.length < 4) {
            CommandUtils.msg(sender, "&e/crypto admin rate <symbol> <rate>");
            CommandUtils.msg(sender, "&e/crypto admin event <symbol> <±percent>");
            return true;
        }
        String sub = args[1].toLowerCase();
        String symbol = args[2].toUpperCase();
        if (!market.hasAsset(symbol)) {
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_NOT_FOUND, "symbol", symbol));
            return true;
        }
        if (sub.equals("rate")) {
            double rateYen = CommandUtils.parsePositiveDouble(args[3]);
            if (rateYen < 0) { CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT); return true; }
            long rateInternal = CommandUtils.toInternal(rateYen);
            market.adminSetRate(symbol, rateInternal);
            CommandUtils.msg(sender, MessageConfig.format(MessageConfig.CRYPTO_RATE_SET,
                    "symbol", symbol, "rate", config.format(rateInternal)));
        } else if (sub.equals("event")) {
            try {
                double pct = Double.parseDouble(args[3]) / 100.0;
                market.adminSetEvent(symbol, pct);
                CommandUtils.msg(sender, "&e" + symbol + "&a にイベント補正を設定しました: &f" + (pct * 100) + "%");
            } catch (NumberFormatException e) {
                CommandUtils.msg(sender, MessageConfig.INVALID_AMOUNT);
            }
        } else {
            showHelp(sender);
        }
        return true;
    }

    private boolean showHelp(CommandSender sender) {
        CommandUtils.msg(sender, "&8&m----&r &6&l仮想通貨コマンド &8&m----");
        CommandUtils.msg(sender, "&e/crypto list &7- 市場レートを表示");
        CommandUtils.msg(sender, "&e/crypto buy <シンボル> <数量> &7- 購入");
        CommandUtils.msg(sender, "&e/crypto sell <シンボル> <数量> &7- 売却");
        CommandUtils.msg(sender, "&e/crypto portfolio &7- 保有状況を表示");
        CommandUtils.msg(sender, "&e/crypto history <シンボル> &7- レート履歴を表示");
        if (sender.hasPermission("alcariseconomy.crypto.admin")) {
            CommandUtils.msg(sender, "&e/crypto admin rate <シンボル> <レート> &7- [管理者] レートを設定");
            CommandUtils.msg(sender, "&e/crypto admin event <シンボル> <±%> &7- [管理者] イベント補正を設定");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("list", "buy", "sell", "portfolio", "history", "admin");
        if (args.length == 2 && (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("history"))) {
            return market.getAllAssets().stream().map(CryptoAsset::getSymbol).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return Arrays.asList("rate", "event");
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            return market.getAllAssets().stream().map(CryptoAsset::getSymbol).collect(Collectors.toList());
        }
        return List.of();
    }
}
