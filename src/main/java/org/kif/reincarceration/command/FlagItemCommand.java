package org.kif.reincarceration.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;

public class FlagItemCommand implements CommandExecutor {

    private final Reincarceration plugin;

    public FlagItemCommand(Reincarceration plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {


        if (!(sender instanceof Player)) {
            ConsoleUtil.sendError("&cThis command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.isOp() && !player.hasPermission("reincarceration.admin.flagitem")) {
            MessageUtil.sendPrefixMessage(player, "&cYou don't have permission to use this command.");
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType().isAir()) {
            MessageUtil.sendPrefixMessage(player, "&cYou must be holding an item to flag it.");
            return true;
        }

        if (ItemUtil.hasReincarcerationFlag(itemInHand)) {
            MessageUtil.sendPrefixMessage(player, "&cThis item is already flagged.");
            return true;
        }

        ItemUtil.addReincarcerationFlag(itemInHand);
        MessageUtil.sendPrefixMessage(player, "&aSuccessfully added flag to the item in your hand.");

        return true;
    }
}