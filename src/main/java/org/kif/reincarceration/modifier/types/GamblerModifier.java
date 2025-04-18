package org.kif.reincarceration.modifier.types;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.kif.reincarceration.util.ConsoleUtil;

import java.util.*;

public class GamblerModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;
    private final Random random = new Random();

    public GamblerModifier(Reincarceration plugin) {
        super("gambler", "Gambler", "A chaotic modifier where everything is left to chance");
        this.plugin = plugin;
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        player.sendMessage(ChatColor.DARK_RED + "You've activated the Gambler modifier. May the odds be ever in your favor!");
        ConsoleUtil.sendDebug("Applied Gambler Modifier to " + player.getName());
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        ConsoleUtil.sendDebug("Removed Gambler Modifier from " + player.getName());
    }

    @Override
    public boolean isSecret() {
        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player)) return;

        // 50% chance the block won't break
        if (random.nextDouble() < 0.5) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "The block refuses to break!");
            return;
        }

        // 10% chance to break the tool
        if (random.nextDouble() < 0.1) {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(ChatColor.RED + "Your tool shatters into pieces!");
        }

        // 30% chance to spawn mobs
        if (random.nextDouble() < 0.3) {
            spawnRandomMob(player, event.getBlock().getLocation());
        }

        // 5% chance for extra loot
        if (random.nextDouble() < 0.05) {
            dropExtraLoot(event.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isActive(player)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        // Handle GamblerMob damage
        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {

            if (event instanceof EntityDamageByEntityEvent entityEvent) {
                Entity damager = entityEvent.getDamager();

                if (damager.hasMetadata("GamblerMob")) {
                    // Increase damage from GamblerMobs
                    double newDamage = event.getDamage() * 1.5;
                    event.setDamage(newDamage);

                    // Chance to apply negative effect
                    if (random.nextDouble() < 0.3) { // 30% chance to apply effect
                        applyRandomNegativeEffect(player);
                    }
                }
            }
        }

        double effect = random.nextDouble();
        if (effect < 0.3) {
            double newDamage = event.getDamage() * 2;
            event.setDamage(newDamage);
            player.sendMessage(ChatColor.RED + "Critical hit! The damage was doubled!");
        } else if (effect < 0.5) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GREEN + "You narrowly avoided the damage!");
        } else if (effect < 0.7) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
            player.sendMessage(ChatColor.GRAY + "The hit slowed you down!");
        }

        // Chance for extra fire duration
        if (cause == EntityDamageEvent.DamageCause.FIRE ||
                cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
            if (random.nextDouble() < 0.3) {
                player.setFireTicks(player.getFireTicks() + 100); // Add 5 seconds of fire
                player.sendMessage(ChatColor.RED + "The flames grow stronger!");
            }
        }
    }

    private void spawnRandomMob(Player player, Location location) {
        EntityType[] mobs = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.WITCH};
        EntityType mobType = mobs[random.nextInt(mobs.length)];
        Entity entity = location.getWorld().spawnEntity(location, mobType);

        entity.setMetadata("GamblerMob", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        if (entity instanceof Mob mob) {
            enhanceMob(mob);
            mob.setTarget(player);
        }

        // Schedule the mob to despawn after 1 minute
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isValid()) {
                    entity.remove();
                }
            }
        }.runTaskLater(plugin, 20 * 60); // 20 ticks * 60 seconds = 1 minute
    }

    private void enhanceMob(Mob mob) {
        // Increase health
        double baseHealth = Objects.requireNonNull(mob.getAttribute(Attribute.MAX_HEALTH)).getBaseValue();
        Objects.requireNonNull(mob.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(baseHealth * 1.5);
        mob.setHealth(Objects.requireNonNull(mob.getAttribute(Attribute.MAX_HEALTH)).getValue());

        // Increase speed and damage
        Objects.requireNonNull(mob.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(
                Objects.requireNonNull(mob.getAttribute(Attribute.MOVEMENT_SPEED)).getBaseValue() * 1.3);
        Objects.requireNonNull(mob.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(
                Objects.requireNonNull(mob.getAttribute(Attribute.ATTACK_DAMAGE)).getBaseValue() * 1.5);

        // Apply random potion effects
        applyRandomPotionEffects(mob);
    }

    private void applyRandomPotionEffects(LivingEntity entity) {
        PotionEffectType[] effects = {
                PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
                PotionEffectType.REGENERATION, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.INVISIBILITY
        };

        int numEffects = random.nextInt(3) + 1; // Apply 1 to 3 effects
        for (int i = 0; i < numEffects; i++) {
            PotionEffectType effect = effects[random.nextInt(effects.length)];
            int duration = 20 * 60 * 5; // 5 minutes
            int amplifier = random.nextInt(2) + 1; // Level 1 or 2
            entity.addPotionEffect(new PotionEffect(effect, duration, amplifier));
        }
    }

    private void applyRandomNegativeEffect(Player player) {
        PotionEffectType[] negativeEffects = {
                PotionEffectType.POISON, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
                PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS, PotionEffectType.HUNGER
        };

        PotionEffectType effect = negativeEffects[random.nextInt(negativeEffects.length)];
        int duration = 20 * (random.nextInt(10) + 5); // 5 to 15 seconds
        int amplifier = random.nextInt(2); // Level 0 or 1
        player.addPotionEffect(new PotionEffect(effect, duration, amplifier));
        player.sendMessage(ChatColor.RED + "The Gambler's curse afflicts you with " + effect.getName() + "!");
    }

    private void dropExtraLoot(Location location) {
        Material[] loots = {Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT, Material.EMERALD};
        Material lootType = loots[random.nextInt(loots.length)];
        location.getWorld().dropItemNaturally(location, new ItemStack(lootType, 1 + random.nextInt(3)));
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getEntity().hasMetadata("GamblerMob")) {
            if (event.getTarget() instanceof Player targetPlayer) {
                String spawnerUUID = event.getEntity().getMetadata("GamblerMob").get(0).asString();

                if (!targetPlayer.getUniqueId().toString().equals(spawnerUUID)) {
                    event.setCancelled(true);
                }
            }
        }
    }
}