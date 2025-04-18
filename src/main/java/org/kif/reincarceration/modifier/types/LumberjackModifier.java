package org.kif.reincarceration.modifier.types;

import me.gypopo.economyshopgui.api.events.PreTransactionEvent;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.Transaction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;
import org.kif.reincarceration.util.MessageUtil;

import java.util.*;

public class LumberjackModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;
    private boolean provideAxeOnDeath;
    private final Set<Material> allowedItems;
    private boolean specialFeatureEnabled;
    private double wolfPackChance;
    private int wolfPackSizeMin;
    private int wolfPackSizeMax;

    public LumberjackModifier(Reincarceration plugin) {
        super("lumberjack", "Lumberjack", "Provides woodcutting benefits and restricts selling to wood-related items");
        this.plugin = plugin;
        this.allowedItems = new HashSet<>();
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modifiers.lumberjack");
        if (config != null) {
            this.provideAxeOnDeath = config.getBoolean("provide_axe_on_death", false);
            this.specialFeatureEnabled = config.getBoolean("special.enabled", false);
            this.wolfPackChance = config.getDouble("special.wolf_pack_chance", 0.1);
            this.wolfPackSizeMin = config.getInt("special.wolf_pack_size_min", 2);
            this.wolfPackSizeMax = config.getInt("special.wolf_pack_size_max", 5);
            List<String> allowedItemsList = config.getStringList("allowed_items");
            if (!allowedItemsList.isEmpty()) {
                for (String item : allowedItemsList) {
                    try {
                        Material material = Material.valueOf(item.toUpperCase());
                        allowedItems.add(material);
                    } catch (IllegalArgumentException e) {
                        ConsoleUtil.sendError("Invalid material in Lumberjack modifier config: " + item);
                    }
                }
            } else {
                ConsoleUtil.sendError("No allowed items specified in Lumberjack modifier config. Using default values.");
                initializeDefaultAllowedItems();
            }
        } else {
            ConsoleUtil.sendError("Lumberjack modifier configuration not found. Using default values.");
            this.provideAxeOnDeath = false;
            this.specialFeatureEnabled = false;
            this.wolfPackChance = 0.1;
            this.wolfPackSizeMin = 2;
            this.wolfPackSizeMax = 5;
            initializeDefaultAllowedItems();
        }
        ConsoleUtil.sendDebug("Lumberjack Modifier Config: Provide Axe On Death = " + provideAxeOnDeath);
        ConsoleUtil.sendDebug("Lumberjack Modifier Config: Special Feature Enabled = " + specialFeatureEnabled);
        ConsoleUtil.sendDebug("Lumberjack Modifier Config: Wolf Pack Chance = " + wolfPackChance);
        ConsoleUtil.sendDebug("Lumberjack Modifier Config: Wolf Pack Size Min = " + wolfPackSizeMin);
        ConsoleUtil.sendDebug("Lumberjack Modifier Config: Wolf Pack Size Max = " + wolfPackSizeMax);
        ConsoleUtil.sendDebug("Lumberjack Modifier Config: Allowed Items = " + allowedItems);
    }

    private void initializeDefaultAllowedItems() {
        // Default allowed items in case the config is not found or invalid
        allowedItems.add(Material.OAK_LOG);
        allowedItems.add(Material.SPRUCE_LOG);
        allowedItems.add(Material.BIRCH_LOG);
        allowedItems.add(Material.JUNGLE_LOG);
        allowedItems.add(Material.ACACIA_LOG);
        allowedItems.add(Material.DARK_OAK_LOG);
        allowedItems.add(Material.CRIMSON_STEM);
        allowedItems.add(Material.WARPED_STEM);
        allowedItems.add(Material.OAK_PLANKS);
        allowedItems.add(Material.SPRUCE_PLANKS);
        allowedItems.add(Material.BIRCH_PLANKS);
        allowedItems.add(Material.JUNGLE_PLANKS);
        allowedItems.add(Material.ACACIA_PLANKS);
        allowedItems.add(Material.DARK_OAK_PLANKS);
        allowedItems.add(Material.CRIMSON_PLANKS);
        allowedItems.add(Material.WARPED_PLANKS);
        allowedItems.add(Material.OAK_SAPLING);
        allowedItems.add(Material.SPRUCE_SAPLING);
        allowedItems.add(Material.BIRCH_SAPLING);
        allowedItems.add(Material.JUNGLE_SAPLING);
        allowedItems.add(Material.ACACIA_SAPLING);
        allowedItems.add(Material.DARK_OAK_SAPLING);
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        ConsoleUtil.sendDebug("Applied Lumberjack Modifier to " + player.getName());
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        ConsoleUtil.sendDebug("Removed Lumberjack Modifier from " + player.getName());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isActive(player) && provideAxeOnDeath) {
            // Use a delayed task to ensure the item is given after respawn
            new BukkitRunnable() {
                @Override
                public void run() {
                    provideWoodenAxe(player);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isActive(event.getPlayer())) return;

        if (event.getBlock().getType().name().endsWith("_LOG") && specialFeatureEnabled) {
            if (Math.random() < wolfPackChance) {
                spawnWolfPack(event.getPlayer());
            }
        }
    }

    private void spawnWolfPack(Player player) {
        Location playerLoc = player.getLocation();
        double spawnDistance = 5.0; // Configurable distance

        List<Wolf> spawnedWolves = new ArrayList<>();
        Random random = new Random();
        int wolfPackSize = random.nextInt(wolfPackSizeMax - wolfPackSizeMin + 1) + wolfPackSizeMin;

        for (int i = 0; i < wolfPackSize; i++) {
            Location spawnLoc = getValidSpawnLocation(playerLoc, spawnDistance);

            if (spawnLoc != null) {
                Wolf wolf = (Wolf) player.getWorld().spawnEntity(spawnLoc, EntityType.WOLF);
                configureWolf(wolf, player);
                spawnedWolves.add(wolf);
            } else {
                // If a valid location isn't found, send a message (or handle accordingly)
                ConsoleUtil.sendError("&cFailed to find a valid spawn location for a wolf.");
            }
        }

        // Periodically update wolf targets and check for despawn
        new BukkitRunnable() {
            int timeElapsed = 0;
            @Override
            public void run() {
                if (timeElapsed >= 120 || spawnedWolves.isEmpty()) {
                    spawnedWolves.forEach(Wolf::remove);
                    this.cancel();
                    return;
                }

                spawnedWolves.removeIf(wolf -> !wolf.isValid());
                spawnedWolves.forEach(wolf -> {
                    if (wolf.getTarget() == null || !wolf.getTarget().equals(player)) {
                        wolf.setTarget(player);
                    }
                });

                timeElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second
    }

    private Location getValidSpawnLocation(Location playerLoc, double distance) {
        World world = playerLoc.getWorld();
        Random random = new Random();
        int maxAttempts = 10;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double xOffset = Math.cos(angle) * distance;
            double zOffset = Math.sin(angle) * distance;

            Location spawnLoc = playerLoc.clone().add(xOffset, 0, zOffset);
            spawnLoc.setY(playerLoc.getY());

            // Check for valid air block
            Block block = spawnLoc.getBlock();
            Block blockAbove = block.getRelative(BlockFace.UP);

            if (block.getType() == Material.AIR && blockAbove.getType() == Material.AIR) {
                return spawnLoc;
            }
        }

        return null;
    }

    private void configureWolf(Wolf wolf, Player target) {
        wolf.setAdult();
        wolf.setAngry(true);
        wolf.setTarget(target);
        wolf.setAware(true);
        Objects.requireNonNull(wolf.getAttribute(Attribute.FOLLOW_RANGE)).setBaseValue(100); // Increase follow range
        Objects.requireNonNull(wolf.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0.4); // Increase speed
        Objects.requireNonNull(wolf.getAttribute(Attribute.ATTACK_DAMAGE)).setBaseValue(4); // Set attack damage
    }

    @Override
    public boolean handleSellTransaction(PreTransactionEvent event) {
        Player player = event.getPlayer();
        ShopItem shopItem = event.getShopItem();
        if (shopItem != null) {
            ItemStack itemStack = shopItem.getItemToGive();
            if (itemStack != null) {
                if (!allowedItems.contains(itemStack.getType())) {
                    event.setCancelled(true);
                    MessageUtil.sendPrefixMessage(player, "&cTransaction Denied - Attempted to sell prohibited items.");
                    ConsoleUtil.sendDebug("Lumberjack modifier cancelled transaction because item " + itemStack.getType() + " is not allowed.");
                    return true;
                }
            }
        }
        return false;
    }

    private void provideWoodenAxe(Player player) {
        if (!player.getInventory().contains(Material.WOODEN_AXE)) {
            player.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
            ConsoleUtil.sendDebug("Provided wooden axe to " + player.getName());
        }
    }

    public void reloadConfig() {
        loadConfig();
    }
}
