package org.kif.reincarceration.util;

import co.killionrevival.curiosities.Curiosities;
import co.killionrevival.curiosities.items.CuriousItem;
import co.killionrevival.curiosities.items.CuriousItemFactory;
import co.killionrevival.curiosities.util.ItemStackUtil;
import co.killionrevival.killioncommons.util.DateUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.rewards.CycleItem;
import org.kif.reincarceration.rewards.CycleReward;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class RewardUtil {
    public static final String CURIOSITIES_PREFIX = "CURIOSITIES_";

    public static CycleReward getCycleRewardForModifier(
            IModifier modifier,
            Reincarceration plugin
    ) {
        final ConfigurationSection modifierConfig = plugin.getConfig().getConfigurationSection("modifiers." + modifier.getId());
        if (modifierConfig == null) {
            ConsoleUtil.sendError("Error parsing rewards: ");
            ConsoleUtil.sendError("Modifier " + modifier.getName() + "does not have a matching config block for it's id.");
            return null;
        }
        final ConfigurationSection rewardsConfig = modifierConfig.getConfigurationSection("rewards");
        if (rewardsConfig == null) {
            ConsoleUtil.sendDebug("Modifier " + modifier.getName() + " has no rewards.");
            return null;
        }
        final BigDecimal money = BigDecimal.valueOf(rewardsConfig.getDouble("money"));
        final List<String> commands = rewardsConfig.getStringList("commands");
        final List<String> commandDescriptions = rewardsConfig.getStringList("commands_desc");

        final ConfigurationSection items = rewardsConfig.getConfigurationSection("items");
        if (items == null) {
            ConsoleUtil.sendDebug("Modifier " + modifier.getName() + " has no item rewards.");

            return new CycleReward(
                    Collections.emptyList(),
                    money,
                    commands,
                    commandDescriptions
            );
        }
        final List<CycleItem> cycleItems = new ArrayList<>();
        final Set<String> materialNames = items.getKeys(false);
        for (final String materialName : materialNames) {
            CycleItem.CycleItemBuilder builder = CycleItem.builder();
            // getInt defaults to 0 if not found, we want default to 1
            final int amount = items.getInt(materialName + ".amount") == 0 ? 1 : items.getInt(materialName + ".amount");
            final boolean stamped = items.getBoolean(materialName + ".stamped");

            if (materialName.startsWith(CURIOSITIES_PREFIX)) {
                for (int i = 0; i < amount; i++) {
                    cycleItems.add(builder.material(materialName).stamped(stamped).build());
                }
                continue;
            }

            final String itemName = items.getString(materialName + ".name");

            builder.material(materialName)
                   .name(itemName)
                   .amount(amount)
                   .stamped(stamped)
                   .lore(items.getStringList(materialName + ".lore"));

            final ConfigurationSection enchantmentsPath = items.getConfigurationSection(materialName + ".enchantments");
            if (enchantmentsPath == null) {
                builder.enchantments(Collections.emptyMap());
                cycleItems.add(builder.build());
                continue;
            }

            final HashMap<String, Integer> enchantmentMap = new HashMap<>();
            final Set<String> enchantmentStrings = enchantmentsPath.getKeys(false);
            for (final String enchantment : enchantmentStrings) {
                enchantmentMap
                        .put(
                                enchantment,
                                enchantmentsPath.getInt(enchantment + ".level")
                        );
            }
            builder.enchantments(enchantmentMap);
            cycleItems.add(builder.build());
        }

        final CycleReward reward = new CycleReward(
                cycleItems,
                money,
                commands,
                commandDescriptions
        );
        ConsoleUtil.sendDebug("Modifier" + modifier.getName() + " cycleReward: " + reward);
        return reward;
    }

    public static ItemStack buildItemStackFromRewardItem(
            final CycleItem item
    ) {
        final String materialName = item.getMaterial();
        if (materialName == null) {
            ConsoleUtil.sendError("CycleItem reward has null name. Returning null itemstack.");
            return null;
        }
        if (materialName.startsWith(CURIOSITIES_PREFIX)) {
            final String curiositiesName = materialName.substring(CURIOSITIES_PREFIX.length());
            final CuriousItem customItem = CuriousItemFactory.createDefault(new NamespacedKey(Curiosities.getNamespace(), curiositiesName.toLowerCase()), null);
            if (customItem == null) {
                ConsoleUtil.sendError("CycleItem reward: " + item.getMaterial() + " has incorrect custom item name. Returning null itemstack.");
                return null;
            }
            if (item.isStamped()) {
                ItemStackUtil.stampItemStack(customItem.getBackingItemStack(), false, null);
            }
            return customItem.getBackingItemStack();
        }

        final Material material = Material.getMaterial(materialName);
        if (material == null) {
            ConsoleUtil.sendError("CycleItem reward: " + item.getMaterial() + " has incorrect material name. Returning null itemstack.");
            return null;
        }

        final ItemStack itemStack = new ItemStack(material);
        itemStack.setAmount(item.getAmount());

        if (item.getName() != null) {
            final ItemMeta meta = itemStack.getItemMeta();
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(item.getName()).decoration(TextDecoration.ITALIC, false));
            itemStack.setItemMeta(meta);
        }

        final List<Component> lore = new ArrayList<>();
        for (final String loreString : item.getLore()) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(loreString).decoration(TextDecoration.ITALIC, false));
        }
        itemStack.lore(lore);

        item.getEnchantments().forEach( (key, value) -> {
            final Enchantment enchantment = Enchantment.getByKey(new NamespacedKey("minecraft", key.toLowerCase()));
            if (enchantment == null) {
                ConsoleUtil.sendError("Enchantment value: " + key + " does not have a matching minecraft namespace key. Skipping.");
                return;
            }
            itemStack.addUnsafeEnchantment(enchantment, value);
        });

        if (item.isStamped()) {
            ItemStackUtil.stampItemStack(itemStack, false, null);
        }

        return itemStack;
    }

    public static ItemStack getPaintingRewardItem(
            final IModifier modifierCompleted,
            final Instant beginDate,
            final Instant endDate
    ) {
        final ItemStack painting = new ItemStack(Material.PAINTING, 1);
        final ItemMeta meta = painting.getItemMeta();
        meta.displayName(
                Component.text("Cycle Completion Plaque")
                         .decorate(TextDecoration.BOLD)
                         .color(TextColor.color(0x50B2C0))
        );
        painting.setItemMeta(meta);


        final List<Component> lore = new ArrayList<>();
        final String cycleComplete = "Cycle completed: " + modifierCompleted.getName();
        final String timeTaken = "Time Taken: " + DateUtil.getTimeStringFromDuration(Duration.between(beginDate, endDate));
        final String dateStarted = "Date started: " + DateUtil.getHumanReadableDateTimeString(beginDate);
        final String dateCompleted = "Finished on: " + DateUtil.getHumanReadableDateTimeString(endDate);

        lore.add(Component.text(cycleComplete).color(TextColor.color(0xFFBA08)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(timeTaken).color(TextColor.color(0xFFBA08)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(dateStarted).color(TextColor.color(0xFFBA08)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(dateCompleted).color(TextColor.color(0xFFBA08)).decoration(TextDecoration.ITALIC, false));

        painting.lore(lore);

        return painting;
    }

    public static List<TextComponent> getRewardLore(
            final CycleReward reward
    ) {
        final List<TextComponent> lore = new ArrayList<>();

        lore.add(Component.text("Rewards:")
                          .decoration(TextDecoration.ITALIC, false)
                          .color(TextColor.color(0x50B2C0)));
        if (reward.getMoney() != null && !(reward.getMoney().compareTo(BigDecimal.ZERO) == 0)) {
            lore.add(
                    Component.text("Money: $" + reward.getMoney())
                             .color(TextColor.color(0xFF4000))
                             .decoration(TextDecoration.ITALIC, false)

            );
        }
        reward.getCommandDescriptions().forEach( it -> lore.add(
                Component.text(it).color(TextColor.color(0xFF4000))
                         .decoration(TextDecoration.ITALIC, false)
        ));
        if (!reward.getItems().isEmpty()) {
            lore.add(Component.text("Items! (Right click to see item rewards)")
                              .decoration(TextDecoration.ITALIC, false)
                              .color(TextColor.color(0xFF4000)));
        }

        return lore;
    }
}
