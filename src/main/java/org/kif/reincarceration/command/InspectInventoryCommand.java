package org.kif.reincarceration.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;
import org.kif.reincarceration.permission.PermissionManager;

import java.util.ArrayList;
import java.util.List;

public class InspectInventoryCommand implements CommandExecutor {

    private final Reincarceration plugin;
    private final PermissionManager permissionManager;

    public InspectInventoryCommand(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("reincarceration.admin.inspectinventory")) {
            MessageUtil.sendPrefixMessage((Player) sender, "&cYou don't have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            MessageUtil.sendPrefixMessage((Player) sender, "&cUsage: /inspectinventory <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.sendPrefixMessage((Player) sender, "&cPlayer not found or not online.");
            return true;
        }

        boolean isAssociated = permissionManager.isAssociatedWithBaseGroup(target.getUniqueId());
        List<ItemStack> violatingItems = new ArrayList<>();

        for (ItemStack item : target.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                boolean hasFlag = ItemUtil.hasReincarcerationFlag(item);
                if ((isAssociated && !hasFlag) || (!isAssociated && hasFlag)) {
                    violatingItems.add(item);
                }
            }
        }

        if (isAssociated) {
            if (violatingItems.isEmpty()) {
                MessageUtil.sendPrefixMessage((Player) sender, "&aNo unflagged items found in " + target.getName() + "'s inventory.");
            } else {
                MessageUtil.sendPrefixMessage((Player) sender, "&cUnflagged items found in " + target.getName() + "'s inventory:");
                listViolatingItems(sender, violatingItems);
            }
        } else {
            if (violatingItems.isEmpty()) {
                MessageUtil.sendPrefixMessage((Player) sender, "&aNo flagged items found in " + target.getName() + "'s inventory.");
            } else {
                MessageUtil.sendPrefixMessage((Player) sender, "&cFlagged items found in " + target.getName() + "'s inventory:");
                listViolatingItems(sender, violatingItems);
            }
        }

        return true;
    }

    private void listViolatingItems(CommandSender sender, List<ItemStack> items) {
        for (ItemStack item : items) {
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().toString();
            MessageUtil.sendPrefixMessage((Player) sender, "&7- &f" + itemName + " &7x" + item.getAmount());
        }
    }
}