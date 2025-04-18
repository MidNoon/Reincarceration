package org.kif.reincarceration.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling block break events with common functionality
 * used by both BlockBreakListener and modifiers like NeolithicModifier.
 */
public class BlockBreakUtil {

    /**
     * Handles the breaking of a block with proper item flagging and special case handling.
     *
     * @param event The BlockBreakEvent
     * @param player The player breaking the block
     * @param block The block being broken
     * @param plugin The plugin instance for scheduling tasks
     * @return true if the event was handled, false otherwise
     */
    public static boolean handleBlockBreak(BlockBreakEvent event, Player player, Block block, Plugin plugin) {
        // Cancel normal drops
        event.setDropItems(false);

        // Handle special cases first
        if (handleSpecialCases(event, block, player, plugin)) {
            return true; // If it was a special case, we're done
        }

        // If not a special case, schedule drops for next tick
        final List<ItemStack> drops = new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));
        final org.bukkit.Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            for (ItemStack drop : drops) {
                ItemUtil.addReincarcerationFlag(drop);
                block.getWorld().dropItem(dropLocation, drop);
                ConsoleUtil.sendDebug("Dropped flagged block item: " + drop.getType().name());
            }
        }, 3L); // 3 tick delay

        // Handle container contents
        handleContainerContents(block);

        return true;
    }

    /**
     * Handles special case blocks like sugar cane and cactus.
     *
     * @param event The BlockBreakEvent
     * @param block The block being broken
     * @param player The player breaking the block
     * @param plugin The plugin instance
     * @return true if a special case was handled, false otherwise
     */
    public static boolean handleSpecialCases(BlockBreakEvent event, Block block, Player player, Plugin plugin) {
        if (block.getType() == Material.CACTUS || block.getType() == Material.SUGAR_CANE) {
            if (!event.isCancelled()) {
                breakConnectedBlocks(block, block.getType(), player);
                return true; // We've handled this special case
            } else {
                ConsoleUtil.sendDebug("Block break was cancelled. Not handling connected blocks for " + block.getType());
            }
        }
        return false; // Not a special case
    }

    /**
     * Breaks connected blocks of the same type (for sugarcane/cactus).
     *
     * @param startBlock The starting block
     * @param material The material type
     * @param player The player breaking the blocks
     */
    public static void breakConnectedBlocks(Block startBlock, Material material, Player player) {
        List<Block> connectedBlocks = new ArrayList<>();
        connectedBlocks.add(startBlock);

        Block above = startBlock.getRelative(BlockFace.UP);
        while (above.getType() == material && connectedBlocks.size() < 3) {
            connectedBlocks.add(above);
            above = above.getRelative(BlockFace.UP);
        }

        // Drop items for all connected blocks
        for (Block block : connectedBlocks) {
            dropFlaggedItem(block.getLocation(), material, player);
            block.setType(Material.AIR);
        }

        ConsoleUtil.sendDebug("Broke and dropped " + connectedBlocks.size() + " connected " + material.name() + " blocks");
    }

    /**
     * Creates and drops a flagged item.
     *
     * @param location The location to drop the item
     * @param material The material type
     * @param player The player for whom to drop the item
     */
    public static void dropFlaggedItem(org.bukkit.Location location, Material material, Player player) {
        ItemStack drop = new ItemStack(material);
        ItemUtil.addReincarcerationFlag(drop);
        player.getWorld().dropItemNaturally(location, drop);
        ConsoleUtil.sendDebug("Dropped flagged item: " + material.name());
    }

    /**
     * Handles the contents of containers when broken.
     *
     * @param block The block being broken
     */
    public static void handleContainerContents(Block block) {
        if (block.getState() instanceof Container) {
            Container container = (Container) block.getState();
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), item);
                    ConsoleUtil.sendDebug("Dropped container item: " + item.getType().name() +
                            ", Flagged: " + ItemUtil.hasReincarcerationFlag(item));
                }
            }
            container.getInventory().clear();
        }
    }

    /**
     * Handles the specific case of snow block breaking.
     *
     * @param event The BlockBreakEvent
     * @param plugin The plugin instance
     */
    public static void handleSnowBlockBreak(BlockBreakEvent event, Plugin plugin) {
        event.setCancelled(true);  // Cancel the original event
        final Block block = event.getBlock();
        final Player player = event.getPlayer();
        final org.bukkit.Location location = block.getLocation();

        // Create a flagged snow block item
        final ItemStack snowBlock = new ItemStack(Material.SNOW_BLOCK);
        ItemUtil.addReincarcerationFlag(snowBlock);

        // Set the block to air (break it)
        block.setType(Material.AIR);

        // Drop the flagged snow block with delay
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            block.getWorld().dropItem(location.add(0.5, 0.5, 0.5), snowBlock);
            ConsoleUtil.sendDebug("Dropped flagged snow block for player: " + player.getName());
        }, 3L);
    }
}