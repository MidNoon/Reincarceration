package org.kif.reincarceration.modifier.types;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;

import java.util.ArrayList;
import java.util.List;

public class CompactModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;
    private int allowedInventorySlots;
    private int allowedHotbarSlots;
    private static final int HOTBAR_SIZE = 9;
    private static final int PLAYER_INVENTORY_SIZE = 36;
    private ItemStack restrictedSlotItem;

    public CompactModifier(Reincarceration plugin) {
        super("compact", "Compact", "Limits the number of usable inventory slots and removes player vault access.");
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modifiers.compact");
        if (config != null) {
            this.allowedInventorySlots = config.getInt("allowed_inventory_slots", 9);
            this.allowedHotbarSlots = config.getInt("allowed_hotbar_slots", 9);
        } else {
            ConsoleUtil.sendError("Compact modifier configuration not found. Using default values.");
            this.allowedInventorySlots = 9;
            this.allowedHotbarSlots = 9;
        }
        ConsoleUtil.sendDebug("Compact Modifier Config: Allowed Inventory Slots = " + allowedInventorySlots + ", Allowed Hotbar Slots = " + allowedHotbarSlots);
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        this.restrictedSlotItem = createRestrictedSlotItem();
        fillRestrictedSlots(player);
        scheduleRecurringCheck(player);
        ConsoleUtil.sendDebug("Applied Compact Modifier to " + player.getName());
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        clearRestrictedSlots(player);
        ConsoleUtil.sendDebug("Removed Compact Modifier from " + player.getName());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isActive(player)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType() == Material.DEAD_BUSH) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isActive(player)) return;

        for (Integer slot : event.getRawSlots()) {
            ItemStack item = player.getOpenInventory().getItem(slot);
            if (item != null && item.getType() == Material.DEAD_BUSH) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isActive(player)) return;

        event.getDrops().removeIf(item -> item != null && item.getType() == Material.DEAD_BUSH);

        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.DEAD_BUSH) {
                inventory.setItem(i, null);
            }
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isActive(player)) return;

        ItemStack item = event.getItem().getItemStack();
        if (item.getType() == Material.DEAD_BUSH) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                fillRestrictedSlots(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void fillRestrictedSlots(Player player) {
        PlayerInventory inventory = player.getInventory();

        for (int i = allowedHotbarSlots; i < HOTBAR_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (isNormalItem(item)) {
                dropOrMoveItem(player, item);
            }
            inventory.setItem(i, restrictedSlotItem.clone());
        }

        for (int i = HOTBAR_SIZE; i < PLAYER_INVENTORY_SIZE - allowedInventorySlots; i++) {
            ItemStack item = inventory.getItem(i);
            if (isNormalItem(item)) {
                dropOrMoveItem(player, item);
            }
            inventory.setItem(i, restrictedSlotItem.clone());
        }
    }

    private boolean isNormalItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR &&
               item.getType() != Material.DEAD_BUSH;
    }

    private void dropOrMoveItem(Player player, ItemStack item) {
        if (!tryMoveToAllowedSlot(player, item)) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            MessageUtil.sendPrefixMessage(player, "&cItem dropped: Not enough space in allowed slots");
        }
    }

    private boolean tryMoveToAllowedSlot(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < allowedHotbarSlots; i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, item);
                return true;
            }
        }

        int startSlot = PLAYER_INVENTORY_SIZE - allowedInventorySlots;
        for (int i = startSlot; i < PLAYER_INVENTORY_SIZE; i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, item);
                return true;
            }
        }

        return false;
    }

    private void clearRestrictedSlots(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < PLAYER_INVENTORY_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.DEAD_BUSH) {
                inventory.setItem(i, null);
            }
        }
    }

    private ItemStack createRestrictedSlotItem() {
        ItemStack deadBush = new ItemStack(Material.DEAD_BUSH);
        ItemMeta meta = deadBush.getItemMeta();

        Component displayName = Component.text("Restricted Slot")
                .color(TextColor.color(0xFF5555))
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("This slot is restricted by the Compact modifier")
                .color(TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        deadBush.setItemMeta(meta);
        ItemUtil.addReincarcerationFlag(deadBush);
        return deadBush;
    }

    private void scheduleRecurringCheck(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(player) || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                fillRestrictedSlots(player);
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30);
    }

    @Override
    public boolean handleVaultAccess(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        event.setCancelled(true);
        MessageUtil.sendPrefixMessage(player, "&cVault Access Denied - You are not allowed to access vaults.");
        return true;
    }
}