package org.kif.reincarceration.modifier.types;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.kif.reincarceration.util.ConsoleUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OreSicknessModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;
    private final Map<Material, OreEffect> oreEffects = new HashMap<>();
    private final boolean effectOnBreak;
    private final boolean effectOnSight;
    private final int effectDuration;
    private final int sightCheckRadius;
    private final double lineOfSightStep;
    private final double fieldOfView;
    private final long checkFrequency;
    private final Map<UUID, BukkitRunnable> activeTasks = new ConcurrentHashMap<>();

    public OreSicknessModifier(Reincarceration plugin) {
        super("ore_sickness", "Ore Sickness", "Applies various effects when breaking or seeing certain ores");
        this.plugin = plugin;

        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modifiers.ore_sickness");
        assert config != null;
        this.effectOnBreak = config.getBoolean("effect_on_break", true);
        this.effectOnSight = config.getBoolean("effect_on_sight", true);
        this.effectDuration = config.getInt("effect_duration", 200);
        this.sightCheckRadius = config.getInt("sight_check_radius", 5);
        this.lineOfSightStep = config.getDouble("line_of_sight_step", 0.1);
        this.fieldOfView = Math.toRadians(config.getDouble("field_of_view", 70));
        this.checkFrequency = config.getLong("check_frequency", 20);

        loadOreEffects(Objects.requireNonNull(config.getConfigurationSection("ore_effects")));
    }

    private void loadOreEffects(ConfigurationSection oresConfig) {
        for (String oreName : oresConfig.getKeys(false)) {
            Material oreMaterial = Material.getMaterial(oreName);
            if (oreMaterial != null) {
                String effectType = oresConfig.getString(oreName + ".type");
                ConfigurationSection effectConfig = oresConfig.getConfigurationSection(oreName);
                assert effectType != null;
                OreEffect effect = createOreEffect(effectType, effectConfig);
                if (effect != null) {
                    oreEffects.put(oreMaterial, effect);
                }
            }
        }
    }

    private OreEffect createOreEffect(String effectType, ConfigurationSection config) {
        switch (effectType.toLowerCase()) {
            case "potion":
                return new PotionOreEffect(
                        PotionEffectType.getByName(Objects.requireNonNull(config.getString("effect"))),
                        config.getInt("duration", effectDuration),
                        config.getInt("amplifier", 0)
                );
//            case "teleport":
//                return new TeleportOreEffect(
//                        config.getInt("min_distance", 3),
//                        config.getInt("max_distance", 10),
//                        config.getStringList("allowed_blocks")
//                );
            case "magnet":
                return new MagnetOreEffect(
                        config.getDouble("radius", 5),
                        config.getBoolean("attract", true),
                        config.getInt("duration", 100)
                );
            case "block_transform":
                return new BlockTransformOreEffect(
                        Material.valueOf(config.getString("from_material")),
                        Material.valueOf(config.getString("to_material")),
                        config.getInt("radius", 3)
                );
            case "inventory_shuffle":
                return new InventoryShuffleOreEffect();
            case "bouncy_blocks":
                return new BouncyBlocksOreEffect(
                        config.getInt("radius", 5),
                        config.getInt("duration", 200)
                );
//            case "vertigo":
//                return new VertigoOreEffect(
//                        config.getInt("duration", 100),
//                        (float) config.getDouble("max_rotation_per_tick", 15.0)
//                );
            case "inventory_weight":
                return new InventoryWeightOreEffect(
                        config.getInt("duration", 200)
                );
            case "hunger":
                return new HungerOreEffect(config.getInt("amount", 2));
//            case "item_drop":
//                return new ItemDropOreEffect(
//                        Material.valueOf(config.getString("item")),
//                        config.getInt("amount", 1)
//                );
            case "sound":
                return new SoundOreEffect(
                        Sound.valueOf(config.getString("sound")),
                        (float) config.getDouble("volume", 1.0),
                        (float) config.getDouble("pitch", 1.0)
                );
            case "sinking":
                return new SinkingEffect(config);
            case "collapse":
                return new CollapseEffect(config);
            case "fire":
                return new FireEffect(config);
            case "item_repulsion":
                return new ItemRepulsionEffect(config);
            case "player_repulsion":
                return new PlayerRepulsionEffect(config);
            case "avoidance":
                return new AvoidanceEffect(config);
            default:
                ConsoleUtil.sendError("Unknown effect type: " + effectType);
                return null;
        }
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        if (effectOnSight) {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    checkPlayerSight(player);
                }
            };
            task.runTaskTimer(plugin, 0L, checkFrequency);
            activeTasks.put(player.getUniqueId(), task);
        }
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        BukkitRunnable task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!effectOnBreak) return;

        Player player = event.getPlayer();
        if (!isActive(player)) return;

        Block block = event.getBlock();
        OreEffect effect = oreEffects.get(block.getType());
        if (effect != null) {
            effect.apply(player);
        }
    }

    private Vector calculateViewDirection(Player player) {
        Location location = player.getLocation();
        double yaw = Math.toRadians(location.getYaw());
        double pitch = Math.toRadians(location.getPitch());

        if (Math.abs(pitch) > Math.PI / 2 - 0.001) {
            pitch = Math.signum(pitch) * (Math.PI / 2 - 0.001);
        }

        return new Vector(
            -Math.sin(yaw) * Math.cos(pitch),
            -Math.sin(pitch),
            Math.cos(yaw) * Math.cos(pitch)
        );
    }

    private Vector[] calculateVisionCone(Vector viewDirection) {
        Vector up = new Vector(0, 1, 0);
        Vector right = viewDirection.getCrossProduct(up).normalize();
        Vector realUp = right.getCrossProduct(viewDirection).normalize();

        double tanFov = Math.tan(fieldOfView / 2);
        Vector topLeft = viewDirection.clone().add(realUp.clone().multiply(tanFov)).subtract(right.clone().multiply(tanFov));
        Vector topRight = viewDirection.clone().add(realUp.clone().multiply(tanFov)).add(right.clone().multiply(tanFov));
        Vector bottomLeft = viewDirection.clone().subtract(realUp.clone().multiply(tanFov)).subtract(right.clone().multiply(tanFov));
        Vector bottomRight = viewDirection.clone().subtract(realUp.clone().multiply(tanFov)).add(right.clone().multiply(tanFov));

        return new Vector[]{topLeft, topRight, bottomLeft, bottomRight};
    }

    private List<Block> findOreBlocksInRange(Player player) {
        List<Block> oreBlocks = new ArrayList<>();
        Location playerLoc = player.getLocation();
        for (int x = -sightCheckRadius; x <= sightCheckRadius; x++) {
            for (int y = -sightCheckRadius; y <= sightCheckRadius; y++) {
                for (int z = -sightCheckRadius; z <= sightCheckRadius; z++) {
                    Block block = playerLoc.getBlock().getRelative(x, y, z);
                    if (oreEffects.containsKey(block.getType())) {
                        oreBlocks.add(block);
                    }
                }
            }
        }
        return oreBlocks;
    }

    private boolean isInVisionCone(Player player, Block block, Vector viewDirection, Vector topLeft, Vector topRight, Vector bottomLeft, Vector bottomRight) {
        Vector playerEyeLocation = player.getEyeLocation().toVector();
        Vector toBlock = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(playerEyeLocation);

        // Check for very close blocks
        if (toBlock.length() <= 2.0) {
            // For close blocks, check if the player is looking roughly in their direction
            return toBlock.normalize().dot(viewDirection) > Math.cos(Math.toRadians(70)); // 70 degrees cone
        }

        if (toBlock.dot(viewDirection) <= 0) return false;

        return isOnPositiveSide(toBlock, viewDirection, topLeft) &&
                isOnPositiveSide(toBlock, viewDirection, topRight) &&
                isOnPositiveSide(toBlock, bottomLeft, viewDirection) &&
                isOnPositiveSide(toBlock, bottomRight, viewDirection);
    }


    private boolean isOnPositiveSide(Vector point, Vector planeNormal, Vector planePoint) {
        return point.subtract(planePoint).dot(planeNormal) >= 0;
    }

    private boolean hasOptimizedLineOfSight(Player player, Block block) {
        Location eyeLoc = player.getEyeLocation();
        Location blockLoc = block.getLocation();
        Location blockCenter = blockLoc.clone().add(0.5, 0.5, 0.5);

        // For very close blocks, always return true
        double distance = eyeLoc.distance(blockCenter);
        if (distance <= 2.0) {
            return true;
        }

        // Direction from block center to player's eyes
        Vector directionToPlayer = eyeLoc.toVector().subtract(blockCenter.toVector()).normalize();

        // Determine visible faces based on direction vector
        List<BlockFace> visibleFaces = new ArrayList<>();

        // Check which faces are potentially visible (max 3 faces)
        if (directionToPlayer.getX() > 0) visibleFaces.add(BlockFace.WEST); // X- face
        if (directionToPlayer.getX() < 0) visibleFaces.add(BlockFace.EAST); // X+ face
        if (directionToPlayer.getY() > 0) visibleFaces.add(BlockFace.DOWN); // Y- face
        if (directionToPlayer.getY() < 0) visibleFaces.add(BlockFace.UP);   // Y+ face
        if (directionToPlayer.getZ() > 0) visibleFaces.add(BlockFace.NORTH); // Z- face
        if (directionToPlayer.getZ() < 0) visibleFaces.add(BlockFace.SOUTH); // Z+ face

        // Shortcut: if block center is visible, return true immediately
        if (hasDirectLineOfSight(eyeLoc, blockCenter, block)) {
            ConsoleUtil.sendDebug("Block center is directly visible");
            return true;
        }

        // Generate check points only for visible faces
        List<Location> checkPoints = new ArrayList<>();

        // First add face centers for quick checking
        for (BlockFace face : visibleFaces) {
            switch (face) {
                case EAST:  checkPoints.add(blockLoc.clone().add(1, 0.5, 0.5)); break;
                case WEST:  checkPoints.add(blockLoc.clone().add(0, 0.5, 0.5)); break;
                case UP:    checkPoints.add(blockLoc.clone().add(0.5, 1, 0.5)); break;
                case DOWN:  checkPoints.add(blockLoc.clone().add(0.5, 0, 0.5)); break;
                case SOUTH: checkPoints.add(blockLoc.clone().add(0.5, 0.5, 1)); break;
                case NORTH: checkPoints.add(blockLoc.clone().add(0.5, 0.5, 0)); break;
            }
        }

        // Then add corners where visible faces intersect
        boolean xMin = visibleFaces.contains(BlockFace.WEST);
        boolean xMax = visibleFaces.contains(BlockFace.EAST);
        boolean yMin = visibleFaces.contains(BlockFace.DOWN);
        boolean yMax = visibleFaces.contains(BlockFace.UP);
        boolean zMin = visibleFaces.contains(BlockFace.NORTH);
        boolean zMax = visibleFaces.contains(BlockFace.SOUTH);

        // Add corners (only where 2+ visible faces meet)
        if (xMin && yMin && zMin) checkPoints.add(blockLoc.clone().add(0, 0, 0));
        if (xMax && yMin && zMin) checkPoints.add(blockLoc.clone().add(1, 0, 0));
        if (xMin && yMax && zMin) checkPoints.add(blockLoc.clone().add(0, 1, 0));
        if (xMax && yMax && zMin) checkPoints.add(blockLoc.clone().add(1, 1, 0));
        if (xMin && yMin && zMax) checkPoints.add(blockLoc.clone().add(0, 0, 1));
        if (xMax && yMin && zMax) checkPoints.add(blockLoc.clone().add(1, 0, 1));
        if (xMin && yMax && zMax) checkPoints.add(blockLoc.clone().add(0, 1, 1));
        if (xMax && yMax && zMax) checkPoints.add(blockLoc.clone().add(1, 1, 1));

        // Add edge midpoints where visible faces meet
        if (xMin && yMin) checkPoints.add(blockLoc.clone().add(0, 0, 0.5));
        if (xMax && yMin) checkPoints.add(blockLoc.clone().add(1, 0, 0.5));
        if (xMin && yMax) checkPoints.add(blockLoc.clone().add(0, 1, 0.5));
        if (xMax && yMax) checkPoints.add(blockLoc.clone().add(1, 1, 0.5));

        if (xMin && zMin) checkPoints.add(blockLoc.clone().add(0, 0.5, 0));
        if (xMax && zMin) checkPoints.add(blockLoc.clone().add(1, 0.5, 0));
        if (xMin && zMax) checkPoints.add(blockLoc.clone().add(0, 0.5, 1));
        if (xMax && zMax) checkPoints.add(blockLoc.clone().add(1, 0.5, 1));

        if (yMin && zMin) checkPoints.add(blockLoc.clone().add(0.5, 0, 0));
        if (yMax && zMin) checkPoints.add(blockLoc.clone().add(0.5, 1, 0));
        if (yMin && zMax) checkPoints.add(blockLoc.clone().add(0.5, 0, 1));
        if (yMax && zMax) checkPoints.add(blockLoc.clone().add(0.5, 1, 1));

        // Check if any point is visible
        for (Location pointLoc : checkPoints) {
            if (hasDirectLineOfSight(eyeLoc, pointLoc, block)) {
                ConsoleUtil.sendDebug("Block visible from optimized point at " +
                        String.format("%.2f,%.2f,%.2f",
                                pointLoc.getX(), pointLoc.getY(), pointLoc.getZ()));
                return true;
            }
        }

        // If no points are visible, try additional points on each visible face
        // (Only if we need extra precision and are willing to sacrifice performance)
        if (distance <= 8.0) { // Only do this extra check for closer blocks
            for (BlockFace face : visibleFaces) {
                // Add 4 additional check points per face (forming an X pattern on the face)
                switch (face) {
                    case EAST:
                        checkPoints.add(blockLoc.clone().add(1, 0.25, 0.25));
                        checkPoints.add(blockLoc.clone().add(1, 0.25, 0.75));
                        checkPoints.add(blockLoc.clone().add(1, 0.75, 0.25));
                        checkPoints.add(blockLoc.clone().add(1, 0.75, 0.75));
                        break;
                    case WEST:
                        checkPoints.add(blockLoc.clone().add(0, 0.25, 0.25));
                        checkPoints.add(blockLoc.clone().add(0, 0.25, 0.75));
                        checkPoints.add(blockLoc.clone().add(0, 0.75, 0.25));
                        checkPoints.add(blockLoc.clone().add(0, 0.75, 0.75));
                        break;
                    case UP:
                        checkPoints.add(blockLoc.clone().add(0.25, 1, 0.25));
                        checkPoints.add(blockLoc.clone().add(0.25, 1, 0.75));
                        checkPoints.add(blockLoc.clone().add(0.75, 1, 0.25));
                        checkPoints.add(blockLoc.clone().add(0.75, 1, 0.75));
                        break;
                    case DOWN:
                        checkPoints.add(blockLoc.clone().add(0.25, 0, 0.25));
                        checkPoints.add(blockLoc.clone().add(0.25, 0, 0.75));
                        checkPoints.add(blockLoc.clone().add(0.75, 0, 0.25));
                        checkPoints.add(blockLoc.clone().add(0.75, 0, 0.75));
                        break;
                    case SOUTH:
                        checkPoints.add(blockLoc.clone().add(0.25, 0.25, 1));
                        checkPoints.add(blockLoc.clone().add(0.25, 0.75, 1));
                        checkPoints.add(blockLoc.clone().add(0.75, 0.25, 1));
                        checkPoints.add(blockLoc.clone().add(0.75, 0.75, 1));
                        break;
                    case NORTH:
                        checkPoints.add(blockLoc.clone().add(0.25, 0.25, 0));
                        checkPoints.add(blockLoc.clone().add(0.25, 0.75, 0));
                        checkPoints.add(blockLoc.clone().add(0.75, 0.25, 0));
                        checkPoints.add(blockLoc.clone().add(0.75, 0.75, 0));
                        break;
                }
            }

            // Check the additional points
            for (Location pointLoc : checkPoints) {
                if (hasDirectLineOfSight(eyeLoc, pointLoc, block)) {
                    ConsoleUtil.sendDebug("Block visible from additional point at " +
                            String.format("%.2f,%.2f,%.2f",
                                    pointLoc.getX(), pointLoc.getY(), pointLoc.getZ()));
                    return true;
                }
            }
        }

        // If none of the points are visible, block is not visible
        return false;
    }

    private boolean hasDirectLineOfSight(Location from, Location to, Block targetBlock) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        // Adaptive step size - smaller steps for closer objects
        double step = Math.min(0.2, distance / 10.0);
        // Ensure minimum step size
        step = Math.max(0.05, step);

        for (double d = 0; d < distance; d += step) {
            Location checkLoc = from.clone().add(direction.clone().multiply(d));
            Block checkBlock = checkLoc.getBlock();

            // If we hit our target block, we have line of sight to this point
            if (checkBlock.equals(targetBlock)) {
                return true;
            }

            // If we hit a different occluding block, no line of sight
            if (!checkBlock.equals(targetBlock) && checkBlock.getType().isOccluding()) {
                return false;
            }
        }

        // If we've reached the end of our ray without hitting an occluding block
        return true;
    }

    /**
     * Update the hasLineOfSight method to use the optimized version
     */
    private boolean hasLineOfSight(Player player, Block block) {
        // For very close blocks, always return true
        Location eyeLoc = player.getEyeLocation();
        Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);
        double distance = eyeLoc.distance(blockLoc);

        if (distance <= 1.5) {
            return true;
        }

        // Use the optimized line of sight detection
        return hasOptimizedLineOfSight(player, block);
    }


    private void checkPlayerSight(Player player) {
        Vector viewDirection = calculateViewDirection(player);
        Vector[] visionCone = calculateVisionCone(viewDirection);
        List<Block> oreBlocks = findOreBlocksInRange(player);

        ConsoleUtil.sendDebug("Checking sight for " + player.getName() + ". Ores in range: " + oreBlocks.size());

        for (Block block : oreBlocks) {
            boolean inCone = isInVisionCone(player, block, viewDirection, visionCone[0], visionCone[1], visionCone[2], visionCone[3]);
            ConsoleUtil.sendDebug("Ore at " + block.getLocation() + " in vision cone: " + inCone);

            if (inCone) {
                boolean hasLineOfSight = hasLineOfSight(player, block);
                ConsoleUtil.sendDebug("Has line of sight to ore at " + block.getLocation() + ": " + hasLineOfSight);

                if (hasLineOfSight) {
                    OreEffect effect = oreEffects.get(block.getType());
                    if (effect != null) {
                        effect.apply(player);
                        ConsoleUtil.sendDebug("Applied effect for ore type: " + block.getType() + " to player: " + player.getName());
                    } else {
                        ConsoleUtil.sendDebug("No effect found for ore type: " + block.getType());
                    }
                }
            }
        }
    }

    private interface OreEffect {
        void apply(Player player);
    }

    private static class PotionOreEffect implements OreEffect {
        private final PotionEffectType effectType;
        private final int duration;
        private final int amplifier;

        PotionOreEffect(PotionEffectType effectType, int duration, int amplifier) {
            this.effectType = effectType;
            this.duration = duration;
            this.amplifier = amplifier;
        }

        @Override
        public void apply(Player player) {
            player.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
        }
    }

    // NOT STABLE
    private static class TeleportOreEffect implements OreEffect {
        private final int minDistance;
        private final int maxDistance;
        private final List<Material> allowedBlocks;

        TeleportOreEffect(int minDistance, int maxDistance, List<String> allowedBlocksStrings) {
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.allowedBlocks = allowedBlocksStrings.stream()
                    .map(Material::valueOf)
                    .collect(Collectors.toList());
        }

        @Override
        public void apply(Player player) {
            Location originalLoc = player.getLocation();
            World world = player.getWorld();
            Random random = new Random();

            for (int attempts = 0; attempts < 50; attempts++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                int distance = random.nextInt(maxDistance - minDistance + 1) + minDistance;

                int x = (int) (Math.cos(angle) * distance);
                int z = (int) (Math.sin(angle) * distance);

                int y = world.getHighestBlockYAt(originalLoc.getBlockX() + x, originalLoc.getBlockZ() + z);

                Location newLoc = new Location(world, originalLoc.getBlockX() + x, y, originalLoc.getBlockZ() + z);

                if (isValidTeleportLocation(newLoc)) {
                    player.teleport(newLoc.add(0.5, 0, 0.5));
                    return;
                }
            }

            Location surfaceLoc = world.getHighestBlockAt(originalLoc).getLocation().add(0, 1, 0);
            player.teleport(surfaceLoc);
        }

        private boolean isValidTeleportLocation(Location loc) {
            Block feetBlock = loc.getBlock();
            Block headBlock = feetBlock.getRelative(BlockFace.UP);

            return allowedBlocks.contains(feetBlock.getType()) &&
                    allowedBlocks.contains(headBlock.getType()) &&
                    feetBlock.getType().isSolid() &&
                    !headBlock.getType().isSolid();
        }
    }

    private static class BlockTransformOreEffect implements OreEffect {
        private final Material fromMaterial;
        private final Material toMaterial;
        private final int radius;

        BlockTransformOreEffect(Material fromMaterial, Material toMaterial, int radius) {
            this.fromMaterial = fromMaterial;
            this.toMaterial = toMaterial;
            this.radius = radius;
        }

        @Override
        public void apply(Player player) {
            Location center = player.getLocation();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block block = center.getBlock().getRelative(x, y, z);
                        if (block.getType() == fromMaterial) {
                            block.setType(toMaterial);
                        }
                    }
                }
            }
        }
    }

    private static class InventoryShuffleOreEffect implements OreEffect {
        @Override
        public void apply(Player player) {
            // Get all inventory contents
            ItemStack[] contents = player.getInventory().getContents();

            // Lists to store items to be shuffled and slots to ignore
            List<ItemStack> itemsToShuffle = new ArrayList<>();
            Map<Integer, ItemStack> slotsToIgnore = new HashMap<>();

            // Indices of armor slots and off hand slot
            int[] armorSlots = {36, 37, 38, 39};
            int offHandSlot = 40;

            // Separate items to shuffle and items to ignore
            for (int i = 0; i < contents.length; i++) {
                int finalI = i;
                if (Arrays.stream(armorSlots).anyMatch(slot -> slot == finalI) || i == offHandSlot) {
                    slotsToIgnore.put(i, contents[i]);
                } else {
                    itemsToShuffle.add(contents[i]);
                }
            }

            // Shuffle the items
            Collections.shuffle(itemsToShuffle);

            // Place shuffled items back into the inventory
            int shuffleIndex = 0;
            for (int i = 0; i < contents.length; i++) {
                if (slotsToIgnore.containsKey(i)) {
                    contents[i] = slotsToIgnore.get(i);
                } else {
                    contents[i] = itemsToShuffle.get(shuffleIndex);
                    shuffleIndex++;
                }
            }

            // Set the new inventory contents
            player.getInventory().setContents(contents);
            player.updateInventory();
        }
    }


    private static class MagnetOreEffect implements OreEffect {
        private final double radius;
        private final boolean attract;
        private final int duration;

        MagnetOreEffect(double radius, boolean attract, int duration) {
            this.radius = radius;
            this.attract = attract;
            this.duration = duration;
        }

        @Override
        public void apply(Player player) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= duration) {
                        this.cancel();
                        return;
                    }
                    for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                        if (entity instanceof Item || entity instanceof FallingBlock) {
                            Vector direction = attract ?
                                    player.getLocation().toVector().subtract(entity.getLocation().toVector()) :
                                    entity.getLocation().toVector().subtract(player.getLocation().toVector());
                            entity.setVelocity(direction.normalize().multiply(0.5));
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(Reincarceration.getPlugin(Reincarceration.class), 0L, 1L);
        }
    }

    private static class BouncyBlocksOreEffect implements OreEffect {
        private final int radius;
        private final int duration;

        BouncyBlocksOreEffect(int radius, int duration) {
            this.radius = radius;
            this.duration = duration;
        }

        @Override
        public void apply(Player player) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= duration) {
                        this.cancel();
                        return;
                    }
                    Location playerLoc = player.getLocation();
                    for (int x = -radius; x <= radius; x++) {
                        for (int y = -radius; y <= radius; y++) {
                            for (int z = -radius; z <= radius; z++) {
                                Block block = playerLoc.getBlock().getRelative(x, y, z);
                                if (block.getType().isSolid()) {
                                    player.getWorld().spawnParticle(Particle.ITEM_SLIME, block.getLocation().add(0.5, 1, 0.5), 1);
                                }
                            }
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(Reincarceration.getPlugin(Reincarceration.class), 0L, 1L);

            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 3));
        }
    }

    // NOT STABLE
    private static class VertigoOreEffect implements OreEffect {
        private final int duration;
        private final float maxRotationPerTick;

        VertigoOreEffect(int duration, float maxRotationPerTick) {
            this.duration = duration;
            this.maxRotationPerTick = maxRotationPerTick;
        }

        @Override
        public void apply(Player player) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= duration) {
                        this.cancel();
                        return;
                    }
                    Location loc = player.getLocation();
                    float currentYaw = loc.getYaw();
                    float rotationAmount = Math.min(15, maxRotationPerTick); // Cap the rotation speed
                    loc.setYaw((currentYaw + rotationAmount) % 360);
                    player.teleport(loc);
                    ticks++;
                }
            }.runTaskTimer(Reincarceration.getPlugin(Reincarceration.class), 0L, 1L);
        }
    }

    private static class InventoryWeightOreEffect implements OreEffect {
        private final int duration;

        InventoryWeightOreEffect(int duration) {
            this.duration = duration;
        }

        @Override
        public void apply(Player player) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= duration) {
                        player.removePotionEffect(PotionEffectType.SLOWNESS);
                        this.cancel();
                        return;
                    }
                    int filledSlots = (int) Arrays.stream(player.getInventory().getContents())
                            .filter(item -> item != null && item.getType() != Material.AIR)
                            .count();
                    int slowness = filledSlots / 9;  // 1 level of slowness for every 9 filled slots
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, slowness, true));
                    ticks += 20;
                }
            }.runTaskTimer(Reincarceration.getPlugin(Reincarceration.class), 0L, 20L);
        }
    }


    private static class HungerOreEffect implements OreEffect {
        private final int amount;

        HungerOreEffect(int amount) {
            this.amount = amount;
        }

        @Override
        public void apply(Player player) {
            int newFoodLevel = Math.max(0, player.getFoodLevel() - amount);
            player.setFoodLevel(newFoodLevel);
        }
    }

    // NOT STABLE
    private static class ItemDropOreEffect implements OreEffect {
        private final Material material;
        private final int amount;

        ItemDropOreEffect(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        @Override
        public void apply(Player player) {
            player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material, amount));
        }
    }

    private static class SoundOreEffect implements OreEffect {
        private final Sound sound;
        private final float volume;
        private final float pitch;

        SoundOreEffect(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        public void apply(Player player) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private class SinkingEffect implements OreEffect {
        private final Set<Material> allowedBlocks;
        private final int duration;
        private final double sinkRate;

        public SinkingEffect(ConfigurationSection config) {
            this.allowedBlocks = config.getStringList("allowed_blocks").stream()
                    .map(Material::valueOf).collect(Collectors.toSet());
            this.duration = config.getInt("duration", 100);
            this.sinkRate = config.getDouble("sink_rate", 0.1);
        }

        @Override
        public void apply(Player player) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= duration) {
                        this.cancel();
                        return;
                    }
                    Location loc = player.getLocation();
                    Block blockBelow = loc.getBlock().getRelative(BlockFace.DOWN);

                    if (!allowedBlocks.contains(blockBelow.getType())) {
                        this.cancel();
                        return;
                    }

                    // Move player slightly toward the center of the block when the effect first occurs
                    if (ticks == 0) {
                        loc.setX(loc.getBlockX() + 0.25);
                        loc.setZ(loc.getBlockZ() + 0.25);
                        player.teleport(loc);
                    }

                    player.teleport(loc.add(0, -sinkRate, 0));
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }


    public static class CollapseEffect implements OreEffect {
        private final Set<Material> allowedBlocks;
        private final int affectedRadius;

        public CollapseEffect(ConfigurationSection config) {
            this.allowedBlocks = config.getStringList("allowed_blocks").stream()
                    .map(Material::valueOf).collect(Collectors.toSet());
            this.affectedRadius = config.getInt("affected_radius", 3);
        }

        @Override
        public void apply(Player player) {
            Location playerLoc = player.getLocation();
            int playerY = playerLoc.getBlockY();

            for (int x = -affectedRadius; x <= affectedRadius; x++) {
                for (int z = -affectedRadius; z <= affectedRadius; z++) {
                    collapseColumn(playerLoc.getBlock().getRelative(x, 0, z), playerY);
                }
            }
        }

        private void collapseColumn(Block baseBlock, int playerY) {
            for (int y = 1; y <= 5; y++) {
                Block block = baseBlock.getRelative(0, y, 0);
                if (block.getY() > playerY && allowedBlocks.contains(block.getType())) {
                    if (canMoveDown(block)) {
                        spawnFallingBlock(block);
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        private void spawnFallingBlock(Block block) {
            Location blockLocation = block.getLocation().add(0.5, 0, 0.5); // Center the falling block
            FallingBlock fallingBlock = block.getWorld().spawnFallingBlock(blockLocation, block.getBlockData());
            fallingBlock.setDropItem(false); // Prevent the block from dropping as an item
            fallingBlock.setHurtEntities(false); // Ensure the block does not hurt entities
        }

        private boolean canMoveDown(Block block) {
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            return blockBelow.getType().isAir();
        }
    }

    private static class FireEffect implements OreEffect {
        private final int duration;

        public FireEffect(ConfigurationSection config) {
            this.duration = config.getInt("duration", 100);
        }

        @Override
        public void apply(Player player) {
            player.setFireTicks(duration);
        }
    }

    private class ItemRepulsionEffect implements OreEffect {
        private final double radius;
        private final double force;
        private final int duration;

        public ItemRepulsionEffect(ConfigurationSection config) {
            this.radius = config.getDouble("radius", 5.0);
            this.force = config.getDouble("force", 0.5);
            this.duration = config.getInt("duration", 100);
        }

        @Override
        public void apply(Player player) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= duration) {
                        this.cancel();
                        return;
                    }
                    for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                        if (entity instanceof Item) {
                            Vector direction = entity.getLocation().toVector().subtract(player.getLocation().toVector());
                            entity.setVelocity(direction.normalize().multiply(force));
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    private class PlayerRepulsionEffect implements OreEffect {
        private final double force;

        public PlayerRepulsionEffect(ConfigurationSection config) {
            this.force = config.getDouble("force", 1.0);
        }

        @Override
        public void apply(Player player) {
            Block oreBlock = getTargetBlock(player);
            Vector direction = player.getLocation().toVector().subtract(oreBlock.getLocation().toVector());
            player.setVelocity(direction.normalize().multiply(force));
        }
    }

    private class AvoidanceEffect implements OreEffect {
        private final Set<Material> allowedBlocks;
        private final int movePeriod;

        public AvoidanceEffect(ConfigurationSection config) {
            this.allowedBlocks = config.getStringList("allowed_blocks").stream()
                    .map(Material::valueOf)
                    .collect(Collectors.toSet());
            this.movePeriod = config.getInt("move_period", 20);
        }

        @Override
        public void apply(Player player) {
            Block oreBlock = getTargetBlock(player);
            if (oreBlock != null && oreEffects.containsKey(oreBlock.getType())) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        switchOre(oreBlock);
                    }
                }.runTaskLater(plugin, movePeriod);
            } else {
                ConsoleUtil.sendDebug("Avoidance effect: No valid ore block found for player " + player.getName());
            }
        }

        private void switchOre(Block oreBlock) {
            List<Block> validAdjacentBlocks = new ArrayList<>();
            boolean hasAirBlockAdjacent = false;

            // Check for adjacent air block
            for (BlockFace face : BlockFace.values()) {
                Block adjacent = oreBlock.getRelative(face);
                if (adjacent.getType() == Material.AIR) {
                    hasAirBlockAdjacent = true;
                    break;
                }
            }

            if (hasAirBlockAdjacent) {
                for (BlockFace face : BlockFace.values()) {
                    Block adjacent = oreBlock.getRelative(face);
                    if (allowedBlocks.contains(adjacent.getType())) {
                        validAdjacentBlocks.add(adjacent);
                    }
                }

                if (!validAdjacentBlocks.isEmpty()) {
                    Block targetBlock = validAdjacentBlocks.get(new Random().nextInt(validAdjacentBlocks.size()));
                    Material oreType = oreBlock.getType();
                    Material targetType = targetBlock.getType();

                    // Switch the blocks
                    targetBlock.setType(oreType);
                    oreBlock.setType(targetType);

                    ConsoleUtil.sendDebug("Avoidance effect: Switched ore from " + oreBlock.getLocation() +
                            " to " + targetBlock.getLocation() +
                            ". Replaced " + targetType + " with " + oreType);
                } else {
                    ConsoleUtil.sendDebug("Avoidance effect: No valid adjacent blocks found for ore at " + oreBlock.getLocation());
                }
            } else {
                ConsoleUtil.sendDebug("Avoidance effect: No adjacent air block found for ore at " + oreBlock.getLocation());
            }
        }
    }

    private Block getTargetBlock(Player player) {
        return player.getTargetBlock(null, 5);
    }

}