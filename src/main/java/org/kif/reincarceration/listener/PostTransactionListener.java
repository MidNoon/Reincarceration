package org.kif.reincarceration.listener;

import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
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
import org.kif.reincarceration.permission.PermissionManager;

import java.sql.SQLException;

public class PostTransactionListener implements Listener {
    private final Reincarceration plugin;
    private final PermissionManager permissionManager;
    private final ModifierManager modifierManager;

    public PostTransactionListener(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
        ModifierModule modifierModule = plugin.getModuleManager().getModule(ModifierModule.class);
        this.modifierManager = modifierModule.getModifierManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostTransaction(PostTransactionEvent event) throws SQLException {
        ConsoleUtil.sendDebug("PostTransaction Entering");
        ConsoleUtil.sendDebug("Event details: " + event.toString());

        Player player = event.getPlayer();
        ConsoleUtil.sendDebug("Player: " + player.getName());

        if (!permissionManager.isAssociatedWithBaseGroup(player.getUniqueId())) {
            ConsoleUtil.sendDebug("Player is not associated with the base group. Exiting.");
            return;
        }

        IModifier activeModifier = modifierManager.getActiveModifier(player);
        if (activeModifier != null && activeModifier.handlePostTransaction(event)) {
            // The modifier handled the event, so we're done
            return;
        }

        if (event.getTransactionType() == Transaction.Type.BUY_SCREEN ||
                event.getTransactionType() == Transaction.Type.BUY_STACKS_SCREEN ||
                event.getTransactionType() == Transaction.Type.SHOPSTAND_BUY_SCREEN ||
                event.getTransactionType() == Transaction.Type.QUICK_BUY) {

            ConsoleUtil.sendDebug("Processing BUY transaction for " + player.getName());

            ItemStack boughtItem = event.getItemStack();
            int amount = event.getAmount();

            if (boughtItem == null) {
                ConsoleUtil.sendError("Bought item is null for player " + player.getName());
                return;
            }

            Transaction.Result result = event.getTransactionResult();
            ConsoleUtil.sendDebug("Transaction result: " + result);

            if (result == Transaction.Result.SUCCESS || result == Transaction.Result.SUCCESS_COMMANDS_EXECUTED) {
                ConsoleUtil.sendDebug("Processing purchase: " + boughtItem.getType() + " x" + amount);

            } else {
                ConsoleUtil.sendDebug("Transaction was not successful. Result: " + result);
            }
        }
    }
}