package org.kif.reincarceration.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kif.reincarceration.cycle.CycleManager;
import org.kif.reincarceration.cycle.CycleModule;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.MessageUtil;

public class CompleteCycleCommand implements CommandExecutor {
    private final CycleManager cycleManager;

    public CompleteCycleCommand(CycleModule cycleModule) {
        this.cycleManager = cycleModule.getCycleManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (!(sender instanceof Player player)) {
            ConsoleUtil.sendError("Command can only be executed by a player.");
            return true;
        }

        if (!player.hasPermission("reincarceration.completecycle")) {
            MessageUtil.sendPrefixMessage(player, "&cInsufficent Permissions");
            return true;
        }

        if (!cycleManager.isPlayerInCycle(player)) {
            MessageUtil.sendPrefixMessage(player, "&cInvalid, Not in cycle");
            ConsoleUtil.sendError(
                    "Player " + player.getName() + " is not in a cycle but has permissions! Review permissions!");
            return true;
        }

        cycleManager.completeCycle(player);

        return true;
    }
}