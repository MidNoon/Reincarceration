package org.kif.reincarceration.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.ModifierModule;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.modifier.core.ModifierManager;
import org.kif.reincarceration.util.*;

import java.sql.SQLException;

public class BlockBreakListener implements Listener {
    private final Reincarceration plugin;
    private final PermissionManager permissionManager;
    private final ModifierManager modifierManager;

    public BlockBreakListener(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);

        ModifierModule modifierModule = plugin.getModuleManager().getModule(ModifierModule.class);
        this.modifierManager = modifierModule.getModifierManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        boolean isAssociated = permissionManager.isAssociatedWithBaseGroup(player.getUniqueId());

        ConsoleUtil.sendDebug("Player " + player.getName() + " broke a block. Associated with base group: " + isAssociated);

        if (isAssociated) {
            // Check if the block is in the blacklist
            if (BlockBlacklist.isBlacklisted(block.getType())) {
                event.setCancelled(true);
                MessageUtil.sendPrefixMessage(player, "&cYou are not allowed to break this block.");
                ConsoleUtil.sendDebug("Player " + player.getName() + " attempted to break a blacklisted block: " + block.getType());
                return;
            }

            if (!canBreakBlock(player, block)) {
                event.setCancelled(true);
                MessageUtil.sendPrefixMessage(player, "&cYou cannot break blocks in this area.");
                return;
            }

            // Check if the block is a snow block
            if (block.getType() == Material.SNOW_BLOCK) {
                BlockBreakUtil.handleSnowBlockBreak(event, plugin);
                return;
            }

            // Check if the block is a sapling
            if (block.getType().name().endsWith("_SAPLING")) {
                ConsoleUtil.sendDebug("Prevented sapling drop for " + player.getName());
                return; // Don't drop anything for saplings
            }

            // Check if the item in hand is flagged
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (!ItemUtil.hasReincarcerationFlag(itemInHand) && !itemInHand.getType().isAir()) {
                event.setCancelled(true);
                MessageUtil.sendPrefixMessage(player, "&cYou can only break blocks with flagged items.");
                ConsoleUtil.sendDebug("Player " + player.getName() + " attempted to break a block with an unflagged item: " + itemInHand.getType());
                return;
            }

            try {
                IModifier activeModifier = modifierManager.getActiveModifier(player);
                if (activeModifier != null && activeModifier.handleBlockBreak(event)) {
                    // The modifier handled the event, so we're done
                    return;
                }

                // If we get here, either there was no active modifier or it didn't handle the event
                BlockBreakUtil.handleBlockBreak(event, player, block, plugin);

            } catch (SQLException e) {
                ConsoleUtil.sendError("Error handling block break: " + e.getMessage());
                event.setCancelled(true);
            }
        }
    }

    private boolean canBreakBlock(Player player, Block block) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(block.getLocation());
        com.sk89q.worldguard.LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        ApplicableRegionSet set = query.getApplicableRegions(loc);
        if (set.size() == 0) {
            // If there are no regions, allow breaking
            ConsoleUtil.sendDebug("No WorldGuard regions at " + block.getLocation() + ", allowing break");
            return true;
        }

        boolean canBreak = query.testState(loc, localPlayer, Flags.BLOCK_BREAK);
        ConsoleUtil.sendDebug("WorldGuard check for " + player.getName() + " at " + block.getLocation() + ": canBreak = " + canBreak);
        return canBreak;
    }
}