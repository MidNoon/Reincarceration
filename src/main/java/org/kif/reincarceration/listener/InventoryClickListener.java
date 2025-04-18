package org.kif.reincarceration.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;

public class InventoryClickListener implements Listener {
    private final Reincarceration plugin;
    private final PermissionManager permissionManager;

    public InventoryClickListener(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (player.isOp()) return;

        boolean isAssociated = permissionManager.isAssociatedWithBaseGroup(player.getUniqueId());

        if (!isAssociated) return;

        String inventoryTitle = event.getView().getTitle();

        // Check if the click is within a custom GUI screen
        if (inventoryTitle.contains("Reincarceration") || inventoryTitle.contains("Player Info") ||
                inventoryTitle.contains("Start Cycle") || inventoryTitle.contains("Rank Up") ||
                inventoryTitle.contains("Modifier List") || inventoryTitle.contains("Online Players") ||
                inventoryTitle.contains("Complete Cycle") || inventoryTitle.contains("Quit Cycle")) {
            // Allow all interactions within custom GUIs
            return;
        }

        // Existing crafting logic
        if (event.getInventory().getType() == InventoryType.WORKBENCH ||
                event.getInventory().getType() == InventoryType.CRAFTING) {
            if (event.getSlotType() == InventoryType.SlotType.RESULT) {
                ItemStack craftedItem = event.getCurrentItem();
                if (craftedItem != null && !craftedItem.getType().isAir() && ItemUtil.hasReincarcerationFlag(craftedItem)) {
                    ConsoleUtil.sendDebug("Crafted flagged item: " + craftedItem.getType().name() + " x" + craftedItem.getAmount());
                }
            }
            // Allow all interactions within crafting inventories
            return;
        }

        // New restriction logic
        if (isAssociated) {
            ItemStack clickedItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // Check clicked item
            if (clickedItem != null && !clickedItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(clickedItem)) {
                // Allow interactions within player inventory
                if (event.getClickedInventory() != player.getInventory()) {
                    event.setCancelled(true);
                    MessageUtil.sendPrefixMessage(player, "&cYou cannot interact with unflagged items outside your inventory.");
                    ConsoleUtil.sendDebug("Cancelled inventory click for " + player.getName() + " on unflagged item: " + clickedItem.getType().name());
                    return;
                }
            }

            // Check cursor item (for placing items)
            if (cursorItem != null && !cursorItem.getType().isAir() && !ItemUtil.hasReincarcerationFlag(cursorItem)) {
                // Allow placing items into player inventory
                if (event.getClickedInventory() != player.getInventory()) {
                    event.setCancelled(true);
                    MessageUtil.sendPrefixMessage(player, "&cYou cannot place unflagged items outside your inventory.");
                    ConsoleUtil.sendDebug("Cancelled inventory click for " + player.getName() + " with unflagged cursor item: " + cursorItem.getType().name());
                }
            }
        }
    }
}
