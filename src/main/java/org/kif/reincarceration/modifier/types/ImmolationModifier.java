package org.kif.reincarceration.modifier.types;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.bukkit.event.entity.EntityCombustEvent;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ImmolationModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final Random random = new Random();

    private int checkInterval;
    private int fireDuration;
    private int spreadFireDuration;
    private double spreadRadius;
    private boolean spreadFireEnabled;
    private double immolationChance;

    public ImmolationModifier(Reincarceration plugin) {
        super("immolation", "Immolation", "Players will spontaneously combust, advised to keep close to water sources");
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modifiers.immolation");
        if (config != null) {
            checkInterval = config.getInt("check_interval", 100);
            fireDuration = config.getInt("fire_duration", 200);
            spreadFireDuration = config.getInt("spread_fire_duration", 100);
            spreadRadius = config.getDouble("spread_radius", 5.0);
            spreadFireEnabled = config.getBoolean("spread_fire_enabled", true);
            immolationChance = config.getDouble("immolation_chance", 0.1);
        } else {
            ConsoleUtil.sendError("Immolation modifier configuration not found. Using default values.");
        }
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> checkAndApplyFire(player), 0L, checkInterval);
        activeTasks.put(player.getUniqueId(), task);
        ConsoleUtil.sendDebug("ImmolationModifier applied to " + player.getName());
        ConsoleUtil.sendDebug("ImmolationModifier Active on " + player.getName() + "? " + isActive(player));
    }

    @Override
    public void remove(Player player) {
        ConsoleUtil.sendDebug("ImmolationModifier Active on " + player.getName() + "? " + isActive(player));
        super.remove(player);
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        player.setFireTicks(0);
        ConsoleUtil.sendDebug("ImmolationModifier removed from " + player.getName());
    }

    private void checkAndApplyFire(Player player) {
        if (!player.isOnline()) {
            remove(player);
            return;
        }
        ConsoleUtil.sendDebug("ImmolationModifier Active on " + player.getName() + "? " + isActive(player));

        // Remove fire resistance effect if present
        if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            ConsoleUtil.sendDebug("Removed fire resistance from " + player.getName());
        }

        ConsoleUtil.sendDebug("Checking fire for " + player.getName() + ". Current fire ticks: " + player.getFireTicks());

        if (player.getFireTicks() > 0) {
            player.setFireTicks(fireDuration);
            ConsoleUtil.sendDebug("Reapplied fire to " + player.getName() + ". New fire ticks: " + player.getFireTicks());
            if (spreadFireEnabled) {
                spreadFireToNearbyPlayers(player);
            }
        } else if (random.nextDouble() < immolationChance) {
            player.setFireTicks(fireDuration);
            ConsoleUtil.sendDebug(player.getName() + " spontaneously combusted. Fire ticks: " + player.getFireTicks());
        }
    }

    private void spreadFireToNearbyPlayers(Player sourcePlayer) {
        ConsoleUtil.sendDebug("Attempting to spread fire from " + sourcePlayer.getName());
        for (Player nearbyPlayer : sourcePlayer.getWorld().getPlayers()) {
            if (nearbyPlayer != sourcePlayer && !isActive(nearbyPlayer) &&
                nearbyPlayer.getLocation().distance(sourcePlayer.getLocation()) <= spreadRadius) {
                nearbyPlayer.setFireTicks(spreadFireDuration);
                ConsoleUtil.sendDebug("Spread fire to " + nearbyPlayer.getName() + ". Fire ticks: " + nearbyPlayer.getFireTicks());
            }
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            ConsoleUtil.sendDebug("ImmolationModifier Active on onEntityCombust " + player.getName() + "? " + isActive(player));
            if (isActive(player)) {
                event.setDuration((long)fireDuration);
                ConsoleUtil.sendDebug("EntityCombustEvent: Set fire duration for " + player.getName() + " to " + fireDuration);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isActive(player)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    equipFireResistantBoots(player);
                }
            }.runTaskLater(plugin, 5L);
        }
    }


    private void equipFireResistantBoots(Player player) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modifiers.immolation.boots");
        ConsoleUtil.sendDebug("ImmolationModifier Active on " + player.getName() + "? " + isActive(player));
        int durability = config != null ? config.getInt("durability", 5) : 5;
        int repairCost = config != null ? config.getInt("repair_cost", 30) : 30;
        int fireProtectionLevel = config != null ? config.getInt("fire_protection_level", 2) : 2;

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.FIRE_PROTECTION, fireProtectionLevel, true);

            // Set custom durability
            if (meta instanceof Damageable) {
                Damageable damageable = (Damageable) meta;
                damageable.setDamage(boots.getType().getMaxDurability() - durability);
            }

            // Set repair cost using NBT
            NamespacedKey repairCostKey = new NamespacedKey(plugin, "RepairCost");
            meta.getPersistentDataContainer().set(repairCostKey, PersistentDataType.INTEGER, repairCost);

            boots.setItemMeta(meta);
        }

        // Add the Reincarceration flag to the boots
        ItemUtil.addReincarcerationFlag(boots);

        player.getInventory().setBoots(boots);
        ConsoleUtil.sendDebug("Equipped " + player.getName() + " with flagged fire-resistant boots (" + durability + " durability, " + repairCost + " repair cost)");
    }
}