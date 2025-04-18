package org.kif.reincarceration.listener;

import com.Acrobot.ChestShop.Events.Economy.CurrencyCheckEvent;
import com.Acrobot.ChestShop.Events.Economy.CurrencyTransferEvent;
import com.Acrobot.ChestShop.Events.PreTransactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.data.DataManager;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.MessageUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

public class ChestShopListener implements Listener {
    private final Reincarceration plugin;
    private final PermissionManager permissionManager;
    private final DataManager dataManager;

    public ChestShopListener(
            final Reincarceration plugin
    ) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
        this.dataManager = plugin.getModuleManager().getModule(DataModule.class).getDataManager();
    }

    /**
     * Handle the pre-transaction currency check for associated players
     * This ensures the transaction doesn't fail before our transfer event can process
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreTransaction(final PreTransactionEvent event) {
        if (event.isCancelled()) {
            return;
        }

        // Only care about buy transactions
        if (event.getTransactionType() != com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.BUY) {
            return;
        }

        Player client = event.getClient();
        UUID clientUUID = client.getUniqueId();

        if (!permissionManager.isAssociatedWithBaseGroup(clientUUID)) {
            return;
        }

        try {
            BigDecimal storedBalance = dataManager.getStoredBalance(clientUUID);
            BigDecimal transactionCost = event.getExactPrice();

            if (storedBalance.compareTo(transactionCost) >= 0) {
                ConsoleUtil.sendDebug("Pre-transaction check - " + clientUUID +
                        " has sufficient stored balance: " + storedBalance +
                        " for purchase: " + transactionCost);

            } else {
                ConsoleUtil.sendDebug("Pre-transaction check - " + clientUUID +
                        " has insufficient stored balance: " + storedBalance +
                        " for purchase: " + transactionCost);

                event.setCancelled(PreTransactionEvent.TransactionOutcome.CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
                MessageUtil.sendPrefixMessage(client,
                        "&4You don't have enough funds in your stored balance. Required: " +
                                transactionCost + ", Available: " + storedBalance);
            }
        } catch (SQLException e) {
            event.setCancelled(PreTransactionEvent.TransactionOutcome.CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
            plugin.getLogger().severe("Error checking stored balance in PreTransactionEvent: " + e.getMessage());
            MessageUtil.sendPrefixMessage(client, "&4Database error checking your stored balance. Transaction cancelled.");
        }
    }

    /**
     * Handle currency check events for associated players to bypass regular economy checks
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCurrencyCheck(final CurrencyCheckEvent event) {
        if (!permissionManager.isAssociatedWithBaseGroup(event.getAccount())) {
            return;
        }

        try {
            BigDecimal requiredAmount = event.getAmount();
            BigDecimal storedBalance = dataManager.getStoredBalance(event.getAccount());

            event.hasEnough(storedBalance.compareTo(requiredAmount) >= 0);
            event.setHandled(true);

            ConsoleUtil.sendDebug("Currency check for " + event.getAccount() +
                    ": Required=" + requiredAmount +
                    ", Stored balance=" + storedBalance +
                    ", Result=" + event.hasEnough());
        } catch (SQLException e) {
            event.hasEnough(false);
            event.setHandled(true);
            plugin.getLogger().severe("Error checking stored balance in CurrencyCheckEvent: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCurrencyTransfer(final CurrencyTransferEvent event) {
        if (event.wasHandled()) {
            return;
        }
        final UUID sender = event.getSender();
        final UUID receiver = event.getReceiver();
        final boolean isSellerAction = event.getDirection() == CurrencyTransferEvent.Direction.PARTNER;

        try {
            if (permissionManager.isAssociatedWithBaseGroup(receiver)) {
                final BigDecimal amountGoingToSeller = event.getAmountReceived();
                final BigDecimal storedBalance = dataManager.getStoredBalance(receiver);

                // Divert funds to stored balance
                event.setAmountReceived(BigDecimal.ZERO);
                final BigDecimal newBalance = storedBalance.add(amountGoingToSeller);
                dataManager.setStoredBalance(receiver, newBalance);

                ConsoleUtil.sendDebug("ChestShop Sell: Diverting " + amountGoingToSeller +
                        " from " + receiver + " to stored balance. New total: " + newBalance);
                final Player sellerPlayer = Bukkit.getServer().getPlayer(receiver);
                if (sellerPlayer != null) {
                    MessageUtil.sendPrefixMessage(sellerPlayer,
                            "&4You are currently in a cycle, so your sale proceeds (" +
                                    amountGoingToSeller + ") have been added to your stored balance.");
                }
            }

            else if (permissionManager.isAssociatedWithBaseGroup(sender) && !isSellerAction) {
                final BigDecimal amountBeingSent = event.getAmountSent();
                final BigDecimal storedBalance = dataManager.getStoredBalance(sender);
                if (storedBalance.compareTo(amountBeingSent) >= 0) {
                    final BigDecimal newBalance = storedBalance.subtract(amountBeingSent);
                    dataManager.setStoredBalance(sender, newBalance);
                    event.setAmountSent(BigDecimal.ZERO);
                    ConsoleUtil.sendDebug("ChestShop Buy: Using " + amountBeingSent +
                            " from " + sender + "'s stored balance. Remaining: " + newBalance);
                    final Player buyerPlayer = Bukkit.getServer().getPlayer(sender);
                    if (buyerPlayer != null) {
                        MessageUtil.sendPrefixMessage(buyerPlayer,
                                "&4Your purchase of " + amountBeingSent +
                                        " has been deducted from your stored balance.");
                    }
                } else {
                    event.setHandled(false);
                    ConsoleUtil.sendDebug("ERROR: ChestShop Buy reached CurrencyTransfer with insufficient funds. " +
                            "Required: " + amountBeingSent + ", Available: " + storedBalance);
                }
            }
        } catch (SQLException e) {
            event.setHandled(false);
            plugin.getLogger().severe("Error in CurrencyTransferEvent: " + e.getMessage());
        }
    }
}