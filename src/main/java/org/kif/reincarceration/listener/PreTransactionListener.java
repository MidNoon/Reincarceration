package org.kif.reincarceration.listener;

import me.gypopo.economyshopgui.api.events.PreTransactionEvent;
import me.gypopo.economyshopgui.util.Transaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.modifier.core.ModifierManager;
import org.kif.reincarceration.modifier.core.ModifierModule;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;
import org.kif.reincarceration.permission.PermissionManager;

import java.sql.SQLException;

public class PreTransactionListener implements Listener {
    private final Reincarceration plugin;
    private final PermissionManager permissionManager;
    private final ModifierManager modifierManager;

    public PreTransactionListener(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
        ModifierModule modifierModule = plugin.getModuleManager().getModule(ModifierModule.class);
        this.modifierManager = modifierModule.getModifierManager();
        ConsoleUtil.sendDebug("PreTransactionListener Started");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreTransaction(PreTransactionEvent event) {
        Player player = event.getPlayer();

        ConsoleUtil.sendDebug("PreTransaction Entering for player: " + player.getName());
        ConsoleUtil.sendDebug("Transaction type: " + event.getTransactionType());

        if (!permissionManager.isAssociatedWithBaseGroup(player.getUniqueId())) {
            ConsoleUtil.sendDebug("Player is not associated with the base group. Exiting.");
            return;
        }

        if (!areAllItemsFlagged(player)) {
            MessageUtil.sendPrefixMessage(player, "&cTransaction Denied: Prohibited Items found on Player.");
            event.setCancelled(true);
            return;
        }

        try {
            IModifier activeModifier = modifierManager.getActiveModifier(player);
            boolean handled = false;

            if (isBuyTransaction(event.getTransactionType())) {
                if (activeModifier != null) {
                    handled = activeModifier.handleBuyTransaction(event);
                    ConsoleUtil.sendDebug("Buy transaction " + (handled ? "handled" : "not handled") + " by active modifier.");
                }
                if (!handled) {
                    handleDefaultBuyTransaction(event);
                }
            } else if (isSellTransaction(event.getTransactionType())) {
                if (activeModifier != null) {
                    handled = activeModifier.handleSellTransaction(event);
                    ConsoleUtil.sendDebug("Sell transaction " + (handled ? "handled" : "not handled") + " by active modifier.");
                }
                // No default behavior for sell transactions
            }
        } catch (SQLException e) {
            ConsoleUtil.sendError("Error getting active modifier: " + e.getMessage());
            event.setCancelled(true);
        }
    }

    private void handleDefaultBuyTransaction(PreTransactionEvent event) {
        ItemStack itemStack = event.getShopItem().getItemToGive();
        if (itemStack != null) {
            ItemUtil.addReincarcerationFlag(itemStack);
            ConsoleUtil.sendDebug("Default behavior: Applied flag to purchased item: " + itemStack.getType() + " x" + event.getAmount());
        } else {
            ConsoleUtil.sendDebug("Default behavior: ItemStack is null, couldn't apply flag");
        }
    }

    private boolean isBuyTransaction(Transaction.Type type) {
        return type == Transaction.Type.BUY_SCREEN ||
                type == Transaction.Type.BUY_STACKS_SCREEN ||
                type == Transaction.Type.SHOPSTAND_BUY_SCREEN ||
                type == Transaction.Type.QUICK_BUY;
    }

    private boolean isSellTransaction(Transaction.Type type) {
        return type == Transaction.Type.SELL_SCREEN ||
                type == Transaction.Type.SELL_ALL_SCREEN ||
                type == Transaction.Type.SHOPSTAND_SELL_SCREEN ||
                type == Transaction.Type.SELL_GUI_SCREEN ||
                type == Transaction.Type.SELL_ALL_COMMAND ||
                type == Transaction.Type.AUTO_SELL_CHEST ||
                type == Transaction.Type.QUICK_SELL;
    }

    private boolean areAllItemsFlagged(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && !ItemUtil.hasReincarcerationFlag(item)) {
                ConsoleUtil.sendDebug("Unflagged item found: " + item.getType() + " for player: " + player.getName());
                return false;
            }
        }
        ConsoleUtil.sendDebug("All items are flagged for player: " + player.getName());
        return true;
    }
}