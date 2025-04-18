package org.kif.reincarceration.cycle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.data.DataManager;
import org.kif.reincarceration.economy.EconomyManager;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.modifier.core.ModifierManager;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.rank.RankManager;
import org.kif.reincarceration.rewards.RewardManager;
import org.kif.reincarceration.util.BroadcastUtil;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.MessageUtil;
import org.kif.reincarceration.util.VaultUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

public class CycleManager {
    private final CycleModule cycleModule;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final EconomyManager economyManager;
    private final RankManager rankManager;
    private final PermissionManager permissionManager;
    private final ModifierManager modifierManager;
    private final RewardManager rewardManager;

    private static final String RANDOM_MODIFIER_ID = "random";

    public CycleManager(Reincarceration plugin, CycleModule cycleModule, ConfigManager configManager,
                        DataManager dataManager, EconomyManager economyManager, RankManager rankManager,
                        PermissionManager permissionManager, ModifierManager modifierManager, RewardManager rewardManager) {
        this.cycleModule = cycleModule;
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.economyManager = economyManager;
        this.rankManager = rankManager;
        this.permissionManager = permissionManager;
        this.modifierManager = modifierManager;
        this.rewardManager = rewardManager;

        ConsoleUtil.sendSuccess("CycleManager initialized with all required components");
    }

    public void startNewCycle(Player player, IModifier modifier) {
        BigDecimal entryFee = configManager.getEntryFee();
        boolean isRandomSelection = RANDOM_MODIFIER_ID.equals(modifier.getId());

        BigDecimal currentBalance = economyManager.getBalance(player);

        if (isRandomSelection) {
            BigDecimal discount = configManager.getRandomModifierDiscount();
            entryFee = entryFee.multiply(BigDecimal.ONE.subtract(discount));
        }

        if (!economyManager.hasEnoughBalance(player, entryFee)) {
            MessageUtil.sendPrefixMessage(player, "&cInsufficient Funds. Entry Fee: " + entryFee);
            return;
        }

        // Teleport the player to the start location
        Location startLocation = configManager.getStartLocation();
        player.teleport(startLocation);

        BigDecimal storedBalance = currentBalance.subtract(entryFee);

        player.setHealth(0.0);
        if (economyManager.withdrawMoney(player, currentBalance)) {
            try {
                VaultUtil.ensureVaultCleared(player.getUniqueId().toString(), 3);

                // If it's a random selection, choose a modifier
                if (isRandomSelection) {
                    List<IModifier> availableModifiers = modifierManager.getAllAvailableModifiers(player);
                    modifier = availableModifiers.get(new Random().nextInt(availableModifiers.size()));
                }

                dataManager.recordCycleStart(player, modifier.getId());
                dataManager.setPlayerCycleStatus(player, true);
                rankManager.setPlayerRank(player, 0);
                dataManager.setStoredBalance(player, storedBalance);

                // Remove player from completion groups
                permissionManager.removeFromCompletionGroups(player);

                // Apply the modifier after quarter of a second to not interfere with the player's death
                IModifier finalModifier = modifier;
                Bukkit.getScheduler().runTaskLater(cycleModule.getPlugin(), () -> {
                    try {
                        modifierManager.applyModifier(player, finalModifier);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }, 60L);

                if (isRandomSelection) {
                    BroadcastUtil.broadcastMessage("&c" + player.getName() + " randomly admitted with the " + modifier.getName() + " modifier");
                } else {
                    BroadcastUtil.broadcastMessage("&c" + player.getName() + " admitted with the " + modifier.getName() + " modifier");
                }
            } catch (SQLException e) {
                logSevere("Error starting new cycle: " + e.getMessage());
                MessageUtil.sendPrefixMessage(player, "&cError during cycle start. Your entry fee has been refunded.");
            }
        } else {
            economyManager.setBalance(player, currentBalance); // Refund the entry fee
            ConsoleUtil.sendError("&cAn error occurred while trying to start a new cycle. Please try again later.");
            MessageUtil.sendPrefixMessage(player,
                    "&cAn error occurred while trying to start a new cycle. Please try again later.");
        }
    }

    public void completeCycle(Player player) {
        try {
            if (!dataManager.isPlayerInCycle(player)) {
                MessageUtil.sendPrefixMessage(player, "&cInvalid: Not currently in a cycle.");
                ConsoleUtil.sendError("Player " + player.getName()
                        + " was detected attempting to complete a cycle without being in a cycle. Review player's data and permission setup!");
                return;
            }

            int currentRank = rankManager.getPlayerRank(player);
            int maxRank = configManager.getMaxRank();

            if (currentRank < maxRank) {
                MessageUtil.sendPrefixMessage(player,
                        "&cInsufficent Rank: " + maxRank + " required to complete the cycle.");
                ConsoleUtil.sendError("Player " + player.getName()
                        + " was detected attempting to complete a cycle without reaching the maximum rank. Review player's data and permission setup!");
                return;
            }

            BigDecimal finalRankUpCost = configManager.getRankUpCost(currentRank);
            if (!economyManager.hasEnoughBalance(player, finalRankUpCost)) {
                MessageUtil.sendPrefixMessage(player,
                        "&cInsufficent Funds: " + finalRankUpCost + " required to complete the cycle.");
                return;
            }

            economyManager.withdrawMoney(player, finalRankUpCost);

            BigDecimal storedBalance = dataManager.getStoredBalance(player);
            BigDecimal currentBalance = economyManager.getBalance(player);
            BigDecimal totalBalance = storedBalance.add(currentBalance);

            IModifier activeModifier = modifierManager.getActiveModifier(player);

            dataManager.recordCycleCompletion(player, true);
            rewardManager.setPlayerNeedsReward(player, activeModifier);
            dataManager.setPlayerCycleStatus(player, false);
            dataManager.incrementPlayerCycleCount(player);
            rankManager.setPlayerRank(player, 0);
            economyManager.setBalance(player, totalBalance);
            dataManager.setStoredBalance(player, BigDecimal.ZERO);

            modifierManager.completeModifier(player, activeModifier);
            permissionManager.updateCompletionGroups(player);

            int completedModifiersCount = dataManager.getCompletedModifierCount(player);

            // Reset player's group to the initial entry group (e.g., "citizen")
            permissionManager.resetToDefaultGroup(player);
            permissionManager.addCompletionPrefix(player);
            player.setHealth(0.0);
            VaultUtil.ensureVaultCleared(player.getUniqueId().toString(), 3);

            BroadcastUtil.broadcastMessage("&c" + player.getName() + " completed the cycle with the "
                    + activeModifier.getName() + " modifier");

        } catch (SQLException e) {
            logSevere("Error completing cycle: " + e.getMessage());
            MessageUtil.sendPrefixMessage(player,
                    "&cerror occurred while completing the cycle. Please try again later.");
        }
    }

    public void quitCycle(Player player) {
        try {
            if (!dataManager.isPlayerInCycle(player)) {
                MessageUtil.sendPrefixMessage(player, "&cInvalid: Not currently in a cycle.");
                ConsoleUtil.sendError("Player " + player.getName()
                        + " reached quitCycle method without being in a cycle. Review player's data and permission setup!");
                return;
            }

            int currentRank = rankManager.getPlayerRank(player);

            BigDecimal storedBalance = dataManager.getStoredBalance(player);
            BigDecimal currentBalance = economyManager.getBalance(player);
            BigDecimal totalBalance = storedBalance.add(currentBalance);

            IModifier activeModifier = modifierManager.getActiveModifier(player);

            dataManager.recordCycleCompletion(player, false);
            dataManager.setPlayerCycleStatus(player, false);
            rankManager.setPlayerRank(player, 0);
            economyManager.setBalance(player, totalBalance);
            dataManager.setStoredBalance(player, BigDecimal.ZERO);

            modifierManager.removeModifier(player);
            permissionManager.updateCompletionGroups(player);

            int completedModifiersCount = dataManager.getCompletedModifierCount(player);

            // Reset player's group to the initial entry group (e.g., "citizen")
            permissionManager.resetToDefaultGroup(player);
            if (completedModifiersCount > 0) {
                permissionManager.addCompletionPrefix(player);
            }
            player.setHealth(0.0);
            VaultUtil.ensureVaultCleared(player.getUniqueId().toString(), 3);

            BroadcastUtil.broadcastMessage(
                    "&c" + player.getName() + " has been discharged as a result of their inability to overcome the "
                            + activeModifier.getName() + " modifier");

        } catch (SQLException e) {
            logSevere("Error quitting cycle: " + e.getMessage());
            MessageUtil.sendPrefixMessage(player,
                    "&cAn error occurred while quitting the cycle. Please try again later.");
        }
    }

    public boolean isPlayerInCycle(Player player) {
        try {
            return dataManager.isPlayerInCycle(player);
        } catch (SQLException e) {
            logSevere("Error checking if player is in cycle: " + e.getMessage());
            return false;
        }
    }

    private void log(String message) {
        cycleModule.getPlugin().getLogger().info(message);
    }

    private void logSevere(String message) {
        cycleModule.getPlugin().getLogger().severe(message);
    }
}