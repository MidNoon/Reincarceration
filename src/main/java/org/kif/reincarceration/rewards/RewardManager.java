package org.kif.reincarceration.rewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.economy.EconomyModule;
import org.kif.reincarceration.entity.CycleHistory;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.RewardUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RewardManager {
    private final EconomyModule economyModule;
    private final DataModule dataModule;
    private final Reincarceration plugin;
    private final ConcurrentHashMap<UUID, IModifier> playersNeedingRewards;

    public RewardManager(Reincarceration plugin) {
        this.economyModule = plugin.getModuleManager().getModule(EconomyModule.class);
        this.dataModule = plugin.getModuleManager().getModule(DataModule.class);
        this.plugin = plugin;
        playersNeedingRewards = new ConcurrentHashMap<>();
    }

    public boolean isPlayerNeededReward(final Player player) {
        if (player == null) return false;
        return playersNeedingRewards.containsKey(player.getUniqueId());
    }

    public void setPlayerNeedsReward(@NotNull final Player player, @NotNull final IModifier modifier) {
        playersNeedingRewards.put(player.getUniqueId(), modifier);
    }

    public void rewardPlayer(@NotNull final Player player) {
        try {
            synchronized (playersNeedingRewards) {
                final IModifier modifier = playersNeedingRewards.get(player.getUniqueId());
                if (modifier == null) {
                    ConsoleUtil.sendDebug("No modifier found for player: " + player.getName());
                    playersNeedingRewards.remove(player.getUniqueId());
                    return;
                }

                final CycleReward reward = RewardUtil.getCycleRewardForModifier(modifier, plugin);
                if (reward == null) {
                    ConsoleUtil.sendDebug("No reward configured for " + modifier.getName());
                    playersNeedingRewards.remove(player.getUniqueId());
                    return;
                }

                // Handle money reward
                try {
                    if (economyModule != null && reward.getMoney() != null && !reward.getMoney().equals(BigDecimal.ZERO)) {
                        economyModule.getEconomyManager().depositMoney(player, reward.getMoney());
                        ConsoleUtil.sendDebug("Awarded " + reward.getMoney() + " to player " + player.getName());
                    }
                } catch (Exception e) {
                    ConsoleUtil.sendError("Error depositing money reward: " + e.getMessage());
                }

                // Execute reward commands
                for (final String command : reward.getCommands()) {
                    try {
                        String processedCommand = command.replace("<player>", player.getName());
                        ConsoleUtil.sendDebug("Executing command: " + processedCommand);
                        plugin.getServer().dispatchCommand(
                                plugin.getServer().getConsoleSender(),
                                processedCommand
                        );
                    } catch (Exception e) {
                        ConsoleUtil.sendError("Error executing command: " + e.getMessage());
                    }
                }

                // Get cycle history
                CycleHistory history = null;
                try {
                    history = dataModule.getDataManager()
                            .getLastCycleHistoryForId(
                                    player.getUniqueId(),
                                    modifier.getId()
                            );
                } catch (Exception e) {
                    ConsoleUtil.sendError("Could not get cycle history: " + e.getMessage());
                }

                // Process item rewards one by one for more robust error handling
                for (CycleItem cycleItem : reward.getItems()) {
                    try {
                        ItemStack item = RewardUtil.buildItemStackFromRewardItem(cycleItem);
                        if (item != null) {
                            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                            if (!leftover.isEmpty()) {
                                ConsoleUtil.sendDebug("Player inventory full, dropping item on ground");
                                for (ItemStack drop : leftover.values()) {
                                    player.getWorld().dropItem(player.getLocation(), drop);
                                }
                            }
                        }
                    } catch (Exception e) {
                        ConsoleUtil.sendError("Error creating reward item: " + e.getMessage());
                    }
                }

                // Handle painting reward
                if (history != null) {
                    try {
                        ItemStack paintingReward = RewardUtil.getPaintingRewardItem(
                                modifier,
                                history.getStartTime(),
                                history.getEndTime()
                        );
                        if (paintingReward != null) {
                            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(paintingReward);
                            if (!leftover.isEmpty()) {
                                player.getWorld().dropItem(player.getLocation(), leftover.get(0));
                            }
                        }
                    } catch (Exception e) {
                        ConsoleUtil.sendError("Error creating painting reward: " + e.getMessage());
                    }
                }

                playersNeedingRewards.remove(player.getUniqueId());
            }
        } catch (Exception e) {
            ConsoleUtil.sendError("Unhandled error in rewardPlayer: " + e.getMessage());
            e.printStackTrace();

            // Make sure we don't leave the player in the rewards queue
            if (player != null) {
                playersNeedingRewards.remove(player.getUniqueId());
            }
        }
    }

    public void rewardPlayer(final UUID uuid) {
        final Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            ConsoleUtil.sendError("Tried to reward player but they don't exist!");
            ConsoleUtil.sendError("UUID: " + uuid);
            ConsoleUtil.sendError("Modifier completed: " +
                    playersNeedingRewards.get(uuid).getName()
            );
            return;
        }

        rewardPlayer(player);
    }
}
