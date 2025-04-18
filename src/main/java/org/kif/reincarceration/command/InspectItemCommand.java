package org.kif.reincarceration.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;

public class InspectItemCommand implements CommandExecutor {

    private final Reincarceration plugin;

    public InspectItemCommand(Reincarceration plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ConsoleUtil.sendError("&cThis command can only be used by players.");
            return true;
        }

        if (!player.isOp() && !player.hasPermission("reincarceration.admin.inspectitem")) {
            MessageUtil.sendPrefixMessage(player, "&cYou don't have permission to use this command.");
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType().isAir()) {
            MessageUtil.sendPrefixMessage(player, "&cYou must be holding an item to inspect it.");
            return true;
        }

        boolean hasFlag = ItemUtil.hasReincarcerationFlag(itemInHand);

        if (hasFlag) {
            MessageUtil.sendPrefixMessage(player, "&aThe item in your hand &2IS flagged &afor the reincarceration system.");
        } else {
            MessageUtil.sendPrefixMessage(player, "&cThe item in your hand &4IS NOT flagged &cfor the reincarceration system.");
        }

        // Additional item information
        MessageUtil.sendPrefixMessage(player, "&eItem Details:");
        MessageUtil.sendPrefixMessage(player, "&7- Type: &f" + itemInHand.getType());
        MessageUtil.sendPrefixMessage(player, "&7- Amount: &f" + itemInHand.getAmount());
        if (itemInHand.hasItemMeta()) {
            ItemMeta meta = itemInHand.getItemMeta();
            if (meta.hasDisplayName()) {
                MessageUtil.sendPrefixMessage(player, "&7- Display Name: &f" + meta.getDisplayName());
            }
            if (meta.hasLore()) {
                MessageUtil.sendPrefixMessage(player, "&7- Lore:");
                for (String loreLine : meta.getLore()) {
                    MessageUtil.sendPrefixMessage(player, "&7  &f" + loreLine);
                }
            }
        }

        return true;
    }
}