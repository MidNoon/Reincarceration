package org.kif.reincarceration.listener;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;

import java.util.HashMap;
import java.util.Map;

public class FurnaceListener implements Listener {
    private final Reincarceration plugin;
    private final PermissionManager permissionManager;

    // Use in-memory tracking instead of persistent data
    private final Map<String, Boolean> markedFurnaces = new HashMap<>();

    public FurnaceListener(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof FurnaceInventory)) return;
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        FurnaceInventory furnaceInventory = (FurnaceInventory) event.getInventory();
        Furnace furnace = (Furnace) furnaceInventory.getHolder();

        if (furnace == null) return;

        boolean isReincarcerated = permissionManager.isAssociatedWithBaseGroup(player.getUniqueId());
        boolean hasFurnaceFlag = isFurnaceMarked(furnace.getBlock());

        ItemStack inputItem = furnaceInventory.getSmelting();
        ItemStack fuelItem = furnaceInventory.getFuel();
        ItemStack outputItem = furnaceInventory.getResult();

        ConsoleUtil.sendDebug("Player " + player.getName() + " opening furnace. Reincarcerated: " + isReincarcerated +
                ", Furnace flagged: " + hasFurnaceFlag);
        ConsoleUtil.sendDebug("Furnace contents - Input: " + (inputItem != null ? inputItem.getType() : "empty") +
                ", Fuel: " + (fuelItem != null ? fuelItem.getType() : "empty") +
                ", Output: " + (outputItem != null ? outputItem.getType() : "empty"));

        if (isReincarcerated) {
            // Player is reincarcerated, check if they can open this furnace

            // First check: if furnace has no flag, all items must have flags
            if (!hasFurnaceFlag) {
                boolean allItemsHaveFlags = true;

                // Check input
                if (inputItem != null && !inputItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(inputItem)) {
                    allItemsHaveFlags = false;
                    ConsoleUtil.sendDebug("Input item doesn't have flag: " + inputItem.getType());
                }

                // Check fuel
                if (fuelItem != null && !fuelItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(fuelItem)) {
                    allItemsHaveFlags = false;
                    ConsoleUtil.sendDebug("Fuel item doesn't have flag: " + fuelItem.getType());
                }

                // Check output
                if (outputItem != null && !outputItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(outputItem)) {
                    allItemsHaveFlags = false;
                    ConsoleUtil.sendDebug("Output item doesn't have flag: " + outputItem.getType());
                }

                if (!allItemsHaveFlags) {
                    // If any items lack flags, deny access
                    event.setCancelled(true);
                    MessageUtil.sendPrefixMessage(player, "&cThis furnace contains unflagged items that you cannot access.");
                    ConsoleUtil.sendDebug("Denied reincarcerated player " + player.getName() + " from opening furnace with unflagged items");
                    return;
                }
            } else {
                // Furnace has flag, check if input and fuel have flags
                boolean inputAndFuelHaveFlags = true;

                if (inputItem != null && !inputItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(inputItem)) {
                    inputAndFuelHaveFlags = false;
                    ConsoleUtil.sendDebug("Flagged furnace has unflagged input: " + inputItem.getType());
                }

                if (fuelItem != null && !fuelItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(fuelItem)) {
                    inputAndFuelHaveFlags = false;
                    ConsoleUtil.sendDebug("Flagged furnace has unflagged fuel: " + fuelItem.getType());
                }

                if (!inputAndFuelHaveFlags) {
                    // Remove furnace flag and deny access
                    markFurnace(furnace.getBlock(), false);
                    event.setCancelled(true);
                    MessageUtil.sendPrefixMessage(player, "&cThis furnace contains unflagged items that you cannot access.");
                    ConsoleUtil.sendDebug("Denied reincarcerated player " + player.getName() + " from opening flagged furnace with unflagged items");
                    return;
                }

                // // Flag the output, if present
                // if (outputItem != null && !outputItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(outputItem)) {
                //     plugin.getServer().getScheduler().runTask(plugin, () -> {
                //         ItemStack flaggedOutput = outputItem.clone();
                //         ItemUtil.addReincarcerationFlag(flaggedOutput);
                //         furnaceInventory.setResult(flaggedOutput);
                //         ConsoleUtil.sendDebug("Flagged output item for reincarcerated player: " + outputItem.getType() + " x" + outputItem.getAmount());
                //     });
                // }

                // // Remove furnace flag since we've handled the flagging
                // markFurnace(furnace.getBlock(), false);
                // ConsoleUtil.sendDebug("Removed flag from furnace after reincarcerated player accessed it");
            }
        } else {
            // Player is normal, remove furnace flag and remove any flags from items
            if (hasFurnaceFlag) {
                markFurnace(furnace.getBlock(), false);
                ConsoleUtil.sendDebug("Removed flag from furnace for normal player");
            }

            // Remove flags from items
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Remove flag from output
                if (outputItem != null && !outputItem.getType().isAir() && ItemUtil.hasReincarcerationFlag(outputItem)) {
                    ItemStack unflaggedOutput = outputItem.clone();
                    ItemUtil.removeReincarcerationFlag(unflaggedOutput);
                    furnaceInventory.setResult(unflaggedOutput);
                    ConsoleUtil.sendDebug("Removed flag from output for normal player");
                }

                // Remove flag from input
                if (inputItem != null && !inputItem.getType().isAir() && ItemUtil.hasReincarcerationFlag(inputItem)) {
                    ItemStack unflaggedInput = inputItem.clone();
                    ItemUtil.removeReincarcerationFlag(unflaggedInput);
                    furnaceInventory.setSmelting(unflaggedInput);
                    ConsoleUtil.sendDebug("Removed flag from input for normal player");
                }

                // Remove flag from fuel
                if (fuelItem != null && !fuelItem.getType().isAir() && ItemUtil.hasReincarcerationFlag(fuelItem)) {
                    ItemStack unflaggedFuel = fuelItem.clone();
                    ItemUtil.removeReincarcerationFlag(unflaggedFuel);
                    furnaceInventory.setFuel(unflaggedFuel);
                    ConsoleUtil.sendDebug("Removed flag from fuel for normal player");
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof FurnaceInventory)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Skip if not in one of the furnace slots
        int slot = event.getRawSlot();
        if (slot > 2) return; // Furnace slots are 0 (smelting), 1 (fuel), 2 (result)

        Player player = (Player) event.getWhoClicked();
        FurnaceInventory furnaceInventory = (FurnaceInventory) event.getInventory();
        Furnace furnace = (Furnace) furnaceInventory.getHolder();

        if (furnace == null) return;

        boolean isReincarcerated = permissionManager.isAssociatedWithBaseGroup(player.getUniqueId());
        boolean hasFurnaceFlag = isFurnaceMarked(furnace.getBlock());
        ItemStack clickedItem = event.getCurrentItem();

        // Only care about reincarcerated players interacting with items
        if (isReincarcerated && clickedItem != null && !clickedItem.getType().isAir()) {
            ConsoleUtil.sendDebug("Reincarcerated player " + player.getName() + " clicked on furnace slot " + slot +
                                 ", Item: " + clickedItem.getType() + ", Has flag: " + ItemUtil.hasReincarcerationFlag(clickedItem));

            if (slot == 2) {  // Output slot
                // If output doesn't have flag, check furnace flag
                if (!ItemUtil.hasReincarcerationFlag(clickedItem)) {
                    if (hasFurnaceFlag) {
                        // Furnace has flag, flag the output and remove furnace flag
                        ItemStack flaggedOutput = clickedItem.clone();
            ItemUtil.addReincarcerationFlag(flaggedOutput);
            event.setCurrentItem(flaggedOutput);
            markFurnace(furnace.getBlock(), false);
                        ConsoleUtil.sendDebug("Flagged output and removed furnace flag on click");
                    } else {
                        // No furnace flag and unflagged output - unflag everything and kick player out
                        event.setCancelled(true);
                        unflagFurnaceItems(furnaceInventory);
                        closeInventoryWithMessage(player, "&cThis furnace contains unflagged items that you cannot access.");
                        ConsoleUtil.sendDebug("Kicked player out, unflagged output with no furnace flag");
                        return;
                    }
                }
            } else {  // Input slot (0) or fuel slot (1)
                // If item doesn't have a flag, unflag everything and kick player out
                if (!ItemUtil.hasReincarcerationFlag(clickedItem)) {
                    event.setCancelled(true);
                    unflagFurnaceItems(furnaceInventory);
                    if (hasFurnaceFlag) {
                        markFurnace(furnace.getBlock(), false);
                    }
                    closeInventoryWithMessage(player, "&cThis furnace contains unflagged items that you cannot access.");
                    ConsoleUtil.sendDebug("Kicked player out, unflagged item in slot " + slot);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        final BlockState state = event.getBlock().getState();
        if (!(state instanceof Furnace furnace)) {
            return;
        }

        // Schedule a task to check and potentially mark the furnace after smelting
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Block block = event.getBlock();
            FurnaceInventory inventory = furnace.getInventory();

            ItemStack inputItem = inventory.getSmelting();
            ItemStack fuelItem = inventory.getFuel();

            // Check if both input and fuel have flags
            boolean bothHaveFlags =
                    (inputItem == null || inputItem.getType().isAir() || ItemUtil.hasReincarcerationFlag(inputItem)) &&
                            (fuelItem == null || fuelItem.getType().isAir() || ItemUtil.hasReincarcerationFlag(fuelItem));

            if (bothHaveFlags) {
                markFurnace(block, true);
                ConsoleUtil.sendDebug("Marked furnace at " + block.getLocation() + " after smelting (both items flagged)");
            } else if (isFurnaceMarked(block)) {
                markFurnace(block, false);
                ConsoleUtil.sendDebug("Removed flag from furnace at " + block.getLocation() + " after smelting (not all items flagged)");
            }
        }, 1L); // Run 1 tick later to avoid interfering with vanilla processing
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        final BlockState state = event.getBlock().getState();
        if (!(state instanceof Furnace furnace)) {
            return;
        }
        // Similarly, check and potentially mark the furnace after burning
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Block block = event.getBlock();
            FurnaceInventory inventory = furnace.getInventory();

            ItemStack inputItem = inventory.getSmelting();
            ItemStack fuelItem = inventory.getFuel();

            boolean bothHaveFlags =
                    (inputItem == null || inputItem.getType().isAir() || ItemUtil.hasReincarcerationFlag(inputItem)) &&
                            (fuelItem == null || fuelItem.getType().isAir() || ItemUtil.hasReincarcerationFlag(fuelItem));

            if (bothHaveFlags) {
                markFurnace(block, true);
                ConsoleUtil.sendDebug("Marked furnace at " + block.getLocation() + " after burn (both items flagged)");
            } else if (isFurnaceMarked(block)) {
                markFurnace(block, false);
                ConsoleUtil.sendDebug("Removed flag from furnace at " + block.getLocation() + " after burn (not all items flagged)");
            }
        }, 1L);
    }

    private void markFurnace(Block block, boolean marked) {
        // Use in-memory tracking to avoid disrupting smelting
        String key = getLocationKey(block);
        if (marked) {
            markedFurnaces.put(key, true);
        } else {
            markedFurnaces.remove(key);
        }
    }

    private boolean isFurnaceMarked(Block block) {
        String key = getLocationKey(block);
        return markedFurnaces.containsKey(key) && markedFurnaces.get(key);
    }

    private String getLocationKey(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private void unflagFurnaceItems(FurnaceInventory furnaceInventory) {
        // Unflag all items in the furnace
        ItemStack inputItem = furnaceInventory.getSmelting();
        if (inputItem != null && !inputItem.getType().isAir() && ItemUtil.hasReincarcerationFlag(inputItem)) {
            ItemStack unflaggedInput = inputItem.clone();
            ItemUtil.removeReincarcerationFlag(unflaggedInput);
            furnaceInventory.setSmelting(unflaggedInput);
            ConsoleUtil.sendDebug("Unflagged input item: " + inputItem.getType());
        }

        ItemStack fuelItem = furnaceInventory.getFuel();
        if (fuelItem != null && !fuelItem.getType().isAir() && ItemUtil.hasReincarcerationFlag(fuelItem)) {
            ItemStack unflaggedFuel = fuelItem.clone();
            ItemUtil.removeReincarcerationFlag(unflaggedFuel);
            furnaceInventory.setFuel(unflaggedFuel);
            ConsoleUtil.sendDebug("Unflagged fuel item: " + fuelItem.getType());
        }

        ItemStack outputItem = furnaceInventory.getResult();
        if (outputItem != null && !outputItem.getType().isAir() && ItemUtil.hasReincarcerationFlag(outputItem)) {
            ItemStack unflaggedOutput = outputItem.clone();
            ItemUtil.removeReincarcerationFlag(unflaggedOutput);
            furnaceInventory.setResult(unflaggedOutput);
            ConsoleUtil.sendDebug("Unflagged output item: " + outputItem.getType());
        }
    }

    private void closeInventoryWithMessage(Player player, String message) {
        // Close inventory and send message
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            MessageUtil.sendPrefixMessage(player, message);
        });
    }
}