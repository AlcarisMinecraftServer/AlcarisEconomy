package net.alcaris.plugin.economy.command;

import net.alcaris.plugin.economy.AlcarisEconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class EconomyCommand implements CommandExecutor, TabCompleter {

    private final AdminCommand adminCommand;

    public EconomyCommand(AdminCommand adminCommand) {
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("admin")) {
            CommandUtils.msg(sender, "&8&m----&r &6&lAlcarisEconomy &8&m----");
            CommandUtils.msg(sender, "&e/economy admin <subcommand> &7- 管理者コマンド");
            return true;
        }
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return adminCommand.dispatch(sender, subArgs);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("admin");
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return adminCommand.tabComplete(sender, subArgs);
        }
        return List.of();
    }
}
