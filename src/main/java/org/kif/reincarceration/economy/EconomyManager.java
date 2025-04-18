package org.kif.reincarceration.economy;

import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.kif.reincarceration.util.ConsoleUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EconomyManager {
    private final EconomyModule economyModule;

    public EconomyManager(EconomyModule economyModule) {
        this.economyModule = economyModule;
    }

    private Currency getDefaultCurrency() {
        Currency currency = economyModule.getDefaultCurrency();
        if (currency == null) {
            throw new IllegalStateException("CoinsEngine default currency is not available");
        }
        return currency;
    }

    public boolean hasEnoughBalance(Player player, BigDecimal amount) {
        ConsoleUtil.sendDebug("Checking balance for " + player.getName() + ": has " + amount + "?");
        try {
            Currency currency = getDefaultCurrency();
            double balance = CoinsEngineAPI.getBalance(player, currency);
            boolean hasBalance = balance >= amount.doubleValue();

            ConsoleUtil.sendDebug(String.format("Checked balance for %s: has %.2f? %s",
                    player.getName(), amount, hasBalance));
            return hasBalance;
        } catch (Exception e) {
            logSevere("Failed to check balance: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean withdrawMoney(Player player, BigDecimal amount) {
        ConsoleUtil.sendDebug("Withdrawing " + amount + " from " + player.getName());
        try {
            Currency currency = getDefaultCurrency();
            double currentBalance = CoinsEngineAPI.getBalance(player, currency);
            double amountDouble = amount.doubleValue();

            if (currentBalance >= amountDouble) {
                CoinsEngineAPI.removeBalance(player, currency, amountDouble);
                ConsoleUtil.sendDebug(String.format("Withdrew %.2f from %s. New balance: %.2f",
                        amountDouble, player.getName(), CoinsEngineAPI.getBalance(player, currency)));
                return true;
            } else {
                ConsoleUtil.sendError(String.format("Failed to withdraw %.2f from %s: Insufficient funds",
                        amountDouble, player.getName()));
                return false;
            }
        } catch (Exception e) {
            logSevere("Failed to withdraw money: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void depositMoney(OfflinePlayer player, BigDecimal amount) {
        ConsoleUtil.sendDebug("Depositing " + amount + " to " + player.getName());
        try {
            Currency currency = getDefaultCurrency();
            double amountDouble = amount.doubleValue();

            if (player.isOnline() && player.getPlayer() != null) {
                CoinsEngineAPI.addBalance(player.getPlayer(), currency, amountDouble);
            } else {
                CoinsEngineAPI.addBalance(player.getUniqueId(), currency, amountDouble);
            }

            ConsoleUtil.sendDebug(String.format("Deposited %.2f to %s", amountDouble, player.getName()));
        } catch (Exception e) {
            logSevere("Failed to deposit money: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public BigDecimal getBalance(Player player) {
        ConsoleUtil.sendDebug("Retrieving balance for " + player.getName());
        try {
            Currency currency = getDefaultCurrency();
            double balance = CoinsEngineAPI.getBalance(player, currency);

            ConsoleUtil.sendDebug(String.format("Retrieved balance for %s: %.2f",
                    player.getName(), balance));
            return BigDecimal.valueOf(balance).setScale(2, RoundingMode.FLOOR);
        } catch (Exception e) {
            logSevere("Failed to get balance: " + e.getMessage());
            e.printStackTrace();
            return BigDecimal.ZERO;
        }
    }

    public void setBalance(Player player, BigDecimal amount) {
        ConsoleUtil.sendDebug("Setting balance for " + player.getName() + " to " + amount);
        try {
            Currency currency = getDefaultCurrency();
            double amountDouble = amount.doubleValue();

            CoinsEngineAPI.setBalance(player, currency, amountDouble);

            ConsoleUtil.sendDebug(String.format("Set balance for %s to %.2f",
                    player.getName(), amountDouble));
        } catch (Exception e) {
            logSevere("Failed to set balance: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logSevere(String message) {
        economyModule.getPlugin().getLogger().severe(message);
    }
}