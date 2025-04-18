package org.kif.reincarceration.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.data.DataManager;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.modifier.core.ModifierManager;
import org.kif.reincarceration.modifier.core.ModifierModule;
import org.kif.reincarceration.rewards.RewardManager;
import org.kif.reincarceration.rewards.RewardModule;
import org.kif.reincarceration.util.MessageUtil;
import org.kif.reincarceration.util.ConsoleUtil;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * This command allows administrators to manually give completion rewards to players for specific modifiers.
 * It checks if the player has completed the specified modifier before granting the reward.
 */
public class RewardCommand implements CommandExecutor {

    private final Reincarceration plugin;
    private final ModifierManager modifierManager;
    private final DataManager dataManager;
    private final RewardManager rewardManager;

    /**
     * Constructs a new RewardCommand.
     *
     * @param plugin The main plugin instance.
     */
    public RewardCommand(Reincarceration plugin) {
        this.plugin = plugin;
        this.modifierManager = plugin.getModuleManager().getModule(ModifierModule.class).getModifierManager();
        this.dataManager = plugin.getModuleManager().getModule(DataModule.class).getDataManager();
        this.rewardManager = plugin.getModuleManager().getModule(RewardModule.class).getRewardManager();
    }

    /**
     * Executes the reward command.
     *
     * @param sender  The sender of the command.
     * @param command The command that was executed.
     * @param label   The alias of the command used.
     * @param args    The arguments passed to the command.
     * @return true if the command was successful, false otherwise.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ConsoleUtil.sendError("This command can only be executed by a player.");
            return true;
        }

        if (!player.hasPermission("reincarceration.admin.reward")) {
            MessageUtil.sendPrefixMessage(player, "&cYou don't have permission to use this command.");
            return true;
        }

        if (args.length != 2) {
            MessageUtil.sendPrefixMessage(player, "&cUsage: /rcreward <player> <modifier_id>");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            MessageUtil.sendPrefixMessage(player, "&cPlayer not found.");
            return true;
        }

        String modifierId = args[1];
        IModifier modifier = modifierManager.getModifierById(modifierId);
        if (modifier == null) {
            MessageUtil.sendPrefixMessage(player, "&cInvalid modifier ID.");
            return true;
        }

        try {
            if (!dataManager.hasCompletedModifier(targetPlayer, modifierId)) {
                MessageUtil.sendPrefixMessage(player, "&cPlayer has not completed this modifier.");
                return true;
            }

            rewardManager.setPlayerNeedsReward(targetPlayer, modifier);
            rewardManager.rewardPlayer(targetPlayer);
            MessageUtil.sendPrefixMessage(player, "&aSuccessfully rewarded " + targetPlayer.getName() + " for completing the " + modifier.getName() + " modifier.");
            ConsoleUtil.sendInfo("Admin " + player.getName() + " manually rewarded " + targetPlayer.getName() + " for modifier: " + modifierId);
        } catch (SQLException e) {
            MessageUtil.sendPrefixMessage(player, "&cAn error occurred while checking modifier completion status.");
            ConsoleUtil.sendError("SQL error in RewardCommand: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "SQL error in RewardCommand", e);
        } catch (Exception e) {
            MessageUtil.sendPrefixMessage(player, "&cAn unexpected error occurred while processing the reward.");
            ConsoleUtil.sendError("Unexpected error in RewardCommand: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Unexpected error in RewardCommand", e);
        }

        return true;
    }
}