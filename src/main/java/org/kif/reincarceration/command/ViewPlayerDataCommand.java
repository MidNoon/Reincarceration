package org.kif.reincarceration.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.data.DataManager;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.util.MessageUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class ViewPlayerDataCommand implements CommandExecutor {

    private final Reincarceration plugin;
    private final DataManager dataManager;

    public ViewPlayerDataCommand(Reincarceration plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getModuleManager().getModule(DataModule.class).getDataManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player) sender;

        if (!sender.isOp() && !sender.hasPermission("reincarceration.admin.viewplayerdata")) {
            MessageUtil.sendPrefixMessage(player, "&cYou don't have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            MessageUtil.sendPrefixMessage(player, "&cUsage: /viewplayerdata <player>");
            return true;
        }

        String playerName = args[0];
        Player targetPlayer = Bukkit.getPlayer(playerName);
        UUID playerUUID = targetPlayer != null ? targetPlayer.getUniqueId() : null;

        if (playerUUID == null) {
            MessageUtil.sendPrefixMessage(player, "&cPlayer not found in the database.");
            return true;
        }

        try {
            displayPlayerData(sender, playerUUID, playerName);
        } catch (SQLException e) {
            MessageUtil.sendPrefixMessage(player, "&cError retrieving player data: " + e.getMessage());
        }

        return true;
    }

    private void displayPlayerData(CommandSender sender, UUID playerUUID, String playerName) throws SQLException {

        Player player = (Player) sender;

        MessageUtil.sendPrefixMessage(player, "&6=== Player Data for " + playerName + " ===");
        MessageUtil.sendPrefixMessage(player, "&7UUID: &f" + playerUUID);

        // Player Data
        int currentRank = dataManager.getPlayerRank(player);
        boolean inCycle = dataManager.isPlayerInCycle(player);
        int cycleCount = dataManager.getPlayerCycleCount(player);
        BigDecimal storedBalance = dataManager.getStoredBalance(player);

        MessageUtil.sendPrefixMessage(player, "&7Current Rank: &f" + currentRank);
        MessageUtil.sendPrefixMessage(player, "&7In Cycle: &f" + (inCycle ? "Yes" : "No"));
        MessageUtil.sendPrefixMessage(player, "&7Cycle Count: &f" + cycleCount);
        MessageUtil.sendPrefixMessage(player, "&7Stored Balance: &f" + storedBalance);

        // Active Modifier
        String activeModifier = dataManager.getActiveModifier(player);
        MessageUtil.sendPrefixMessage(player, "&7Active Modifier: &f" + (activeModifier != null ? activeModifier : "None"));

        // Completed Modifiers
        List<String> completedModifiers = dataManager.getCompletedModifiers(player);
        MessageUtil.sendPrefixMessage(player, "&7Completed Modifiers:");
        if (completedModifiers.isEmpty()) {
            MessageUtil.sendPrefixMessage(player, "&f  None");
        } else {
            for (String modifier : completedModifiers) {
                MessageUtil.sendPrefixMessage(player, "&f  - " + modifier);
            }
        }
    }
}