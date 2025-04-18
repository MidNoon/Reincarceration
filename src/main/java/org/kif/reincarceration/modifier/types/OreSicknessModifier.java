package org.kif.reincarceration.modifier.types;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
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
    private final boolean effectOnBreak, effectOnSight;
    private final int effectDuration, sightCheckRadius;
    private final double lineOfSightStep;
    private final double fieldOfView;
    private final long checkFrequency;
    private final Map<UUID, BukkitRunnable> activeTasks = new ConcurrentHashMap<>();

    public OreSicknessModifier(Reincarceration plugin) {
        super("ore_sickness", "Ore Sickness",
                "Applies various effects when breaking or seeing certain ores");
        this.plugin = plugin;
        ConfigurationSection cfg = plugin.getConfig()
                .getConfigurationSection("modifiers.ore_sickness");
        assert cfg != null;
        effectOnBreak = cfg.getBoolean("effect_on_break", true);
        effectOnSight = cfg.getBoolean("effect_on_sight", true);
        effectDuration = cfg.getInt("effect_duration", 200);
        sightCheckRadius = cfg.getInt("sight_check_radius", 5);
        lineOfSightStep = cfg.getDouble("line_of_sight_step", 0.1);
        fieldOfView = Math.toRadians(cfg.getDouble("field_of_view", 70));
        checkFrequency = cfg.getLong("check_frequency", 20);

        loadOreEffects(Objects.requireNonNull(
                cfg.getConfigurationSection("ore_effects")));
    }

    private void loadOreEffects(ConfigurationSection cfg) {
        for (String key : cfg.getKeys(false)) {
            Material m = Material.getMaterial(key);
            if (m == null) continue;
            String type = cfg.getString(key + ".type");
            assert type != null;
            ConfigurationSection sc = cfg.getConfigurationSection(key);
            OreEffect e = createOreEffect(type, sc);
            if (e != null) oreEffects.put(m, e);
        }
    }

    private OreEffect createOreEffect(String type, ConfigurationSection cfg) {
        switch (type.toLowerCase()) {
            case "potion":
                return new PotionOreEffect(
                        PotionEffectType.getByName(Objects.requireNonNull(cfg.getString("effect"))),
                        cfg.getInt("duration", effectDuration),
                        cfg.getInt("amplifier", 0)
                );
            case "magnet":
                return new MagnetOreEffect(
                        cfg.getDouble("radius", 5),
                        cfg.getBoolean("attract", true),
                        cfg.getInt("duration", 100)
                );
            case "block_transform":
                return new BlockTransformOreEffect(
                        Material.valueOf(cfg.getString("from_material")),
                        Material.valueOf(cfg.getString("to_material")),
                        cfg.getInt("radius", 3)
                );
            case "inventory_shuffle": return new InventoryShuffleOreEffect();
            case "bouncy_blocks":
                return new BouncyBlocksOreEffect(
                        cfg.getInt("radius", 5), cfg.getInt("duration", 200)
                );
            case "inventory_weight":
                return new InventoryWeightOreEffect(cfg.getInt("duration", 200));
            case "hunger": return new HungerOreEffect(cfg.getInt("amount", 2));
            case "sound":
                return new SoundOreEffect(
                        Sound.valueOf(cfg.getString("sound")),
                        (float) cfg.getDouble("volume", 1.0),
                        (float) cfg.getDouble("pitch", 1.0)
                );
            case "sinking": return new SinkingEffect(cfg);
            case "collapse": return new CollapseEffect(cfg);
            case "fire": return new FireEffect(cfg);
            case "item_repulsion": return new ItemRepulsionEffect(cfg);
            case "player_repulsion": return new PlayerRepulsionEffect(cfg);
            case "avoidance": return new AvoidanceEffect(cfg);
            default:
                ConsoleUtil.sendError("Unknown effect type: " + type);
                return null;
        }
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        if (!effectOnSight) return;
        BukkitRunnable task = new BukkitRunnable() {
            @Override public void run() {
                checkPlayerSight(player);
            }
        };
        task.runTaskTimer(plugin, 0L, checkFrequency);
        activeTasks.put(player.getUniqueId(), task);
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        BukkitRunnable task = activeTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!effectOnBreak) return;
        Player p = e.getPlayer();
        if (!isActive(p)) return;
        OreEffect ef = oreEffects.get(e.getBlock().getType());
        if (ef != null) ef.apply(p);
    }

    private Vector calculateViewDirection(Player p) {
        Location L = p.getLocation();
        double yaw = Math.toRadians(L.getYaw()),
                pitch = Math.toRadians(L.getPitch());
        // Clamp pitch to avoid edge cases
        if (Math.abs(pitch) > Math.PI / 2 - 0.001) {
            pitch = Math.signum(pitch) * (Math.PI / 2 - 0.001);
        }
        return new Vector(
                -Math.sin(yaw)*Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw)*Math.cos(pitch)
        );
    }

    private Vector[] calculateVisionCone(Vector dir) {
        Vector up = new Vector(0, 1, 0);
        Vector right = dir.clone().getCrossProduct(up).normalize();
        Vector realUp = right.clone().getCrossProduct(dir).normalize();
        double t = Math.tan(fieldOfView / 2);
        return new Vector[] {
                dir.clone().add(realUp.clone().multiply(t))
                        .subtract(right.clone().multiply(t)),
                dir.clone().add(realUp.clone().multiply(t))
                        .add(right.clone().multiply(t)),
                dir.clone().subtract(realUp.clone().multiply(t))
                        .subtract(right.clone().multiply(t)),
                dir.clone().subtract(realUp.clone().multiply(t))
                        .add(right.clone().multiply(t))
        };
    }

    private List<Block> findOreBlocksInRange(Player p) {
        List<Block> ores = new ArrayList<>();
        Location c = p.getLocation();
        for (int x = -sightCheckRadius; x <= sightCheckRadius; x++)
            for (int y = -sightCheckRadius; y <= sightCheckRadius; y++)
                for (int z = -sightCheckRadius; z <= sightCheckRadius; z++) {
                    Block b = c.getBlock().getRelative(x, y, z);
                    if (oreEffects.containsKey(b.getType())) {
                        ores.add(b);
                    }
                }
        ConsoleUtil.sendDebug("Found " + ores.size() + " ore blocks in range for " + p.getName());
        return ores;
    }

    private boolean hasDirectLineOfSight(Location from, Location to, Block tgt) {
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length(),
                step = Math.max(0.05, Math.min(0.2, dist / 10));
        dir.normalize();
        for (double d = 0; d < dist; d += step) {
            Block b = from.clone()
                    .add(dir.clone().multiply(d))
                    .getBlock();
            if (b.equals(tgt)) return true;
            if (!b.equals(tgt) && b.getType().isOccluding()) return false;
        }
        return true;
    }

    private boolean hasLineOfSight(Player p, Block b) {
        Location e = p.getEyeLocation();
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        double distance = e.distance(c);
        if (distance <= 1.5) return true;
        return hasOptimizedLineOfSight(p, b);
    }

    private boolean hasOptimizedLineOfSight(Player player, Block block) {
        Location eye = player.getEyeLocation();
        Location blockLoc = block.getLocation();
        Location center = blockLoc.clone().add(0.5, 0.5, 0.5);
        double distance = eye.distance(center);

        // Very close blocks are always visible
        if (distance <= 2.0) {
            return true;
        }

        // Check if block center is directly visible
        if (hasDirectLineOfSight(eye, center, block)) {
            ConsoleUtil.sendDebug("Block center is directly visible");
            return true;
        }

        // Determine which faces are potentially visible based on direction
        Vector dirToPlayer = eye.toVector().subtract(center.toVector()).normalize();
        List<Location> checkPoints = new ArrayList<>();

        // Add face centers to check points
        if (dirToPlayer.getX() > 0) checkPoints.add(blockLoc.clone().add(0, 0.5, 0.5));   // WEST
        if (dirToPlayer.getX() < 0) checkPoints.add(blockLoc.clone().add(1, 0.5, 0.5));   // EAST
        if (dirToPlayer.getY() > 0) checkPoints.add(blockLoc.clone().add(0.5, 0, 0.5));   // DOWN
        if (dirToPlayer.getY() < 0) checkPoints.add(blockLoc.clone().add(0.5, 1, 0.5));   // UP
        if (dirToPlayer.getZ() > 0) checkPoints.add(blockLoc.clone().add(0.5, 0.5, 0));   // NORTH
        if (dirToPlayer.getZ() < 0) checkPoints.add(blockLoc.clone().add(0.5, 0.5, 1));   // SOUTH

        // Track which sides are visible
        boolean xMin = dirToPlayer.getX() > 0; // WEST
        boolean xMax = dirToPlayer.getX() < 0; // EAST
        boolean yMin = dirToPlayer.getY() > 0; // DOWN
        boolean yMax = dirToPlayer.getY() < 0; // UP
        boolean zMin = dirToPlayer.getZ() > 0; // NORTH
        boolean zMax = dirToPlayer.getZ() < 0; // SOUTH

        // Add corners of visible faces
        if (xMin && yMin && zMin) checkPoints.add(blockLoc.clone().add(0, 0, 0));
        if (xMax && yMin && zMin) checkPoints.add(blockLoc.clone().add(1, 0, 0));
        if (xMin && yMax && zMin) checkPoints.add(blockLoc.clone().add(0, 1, 0));
        if (xMax && yMax && zMin) checkPoints.add(blockLoc.clone().add(1, 1, 0));
        if (xMin && yMin && zMax) checkPoints.add(blockLoc.clone().add(0, 0, 1));
        if (xMax && yMin && zMax) checkPoints.add(blockLoc.clone().add(1, 0, 1));
        if (xMin && yMax && zMax) checkPoints.add(blockLoc.clone().add(0, 1, 1));
        if (xMax && yMax && zMax) checkPoints.add(blockLoc.clone().add(1, 1, 1));

        // Add edge centers
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

        // Check all points
        for (Location point : checkPoints) {
            if (hasDirectLineOfSight(eye, point, block)) {
                ConsoleUtil.sendDebug("Block visible from point at " +
                        String.format("%.2f,%.2f,%.2f", point.getX(), point.getY(), point.getZ()));
                return true;
            }
        }

        // For closer blocks, check additional points
        if (distance <= 8.0) {
            List<Location> extraPoints = new ArrayList<>();

            // Add additional points on each face for more precise detection
            if (xMin) {
                extraPoints.add(blockLoc.clone().add(0, 0.25, 0.25));
                extraPoints.add(blockLoc.clone().add(0, 0.25, 0.75));
                extraPoints.add(blockLoc.clone().add(0, 0.75, 0.25));
                extraPoints.add(blockLoc.clone().add(0, 0.75, 0.75));
            }
            if (xMax) {
                extraPoints.add(blockLoc.clone().add(1, 0.25, 0.25));
                extraPoints.add(blockLoc.clone().add(1, 0.25, 0.75));
                extraPoints.add(blockLoc.clone().add(1, 0.75, 0.25));
                extraPoints.add(blockLoc.clone().add(1, 0.75, 0.75));
            }
            if (yMin) {
                extraPoints.add(blockLoc.clone().add(0.25, 0, 0.25));
                extraPoints.add(blockLoc.clone().add(0.25, 0, 0.75));
                extraPoints.add(blockLoc.clone().add(0.75, 0, 0.25));
                extraPoints.add(blockLoc.clone().add(0.75, 0, 0.75));
            }
            if (yMax) {
                extraPoints.add(blockLoc.clone().add(0.25, 1, 0.25));
                extraPoints.add(blockLoc.clone().add(0.25, 1, 0.75));
                extraPoints.add(blockLoc.clone().add(0.75, 1, 0.25));
                extraPoints.add(blockLoc.clone().add(0.75, 1, 0.75));
            }
            if (zMin) {
                extraPoints.add(blockLoc.clone().add(0.25, 0.25, 0));
                extraPoints.add(blockLoc.clone().add(0.25, 0.75, 0));
                extraPoints.add(blockLoc.clone().add(0.75, 0.25, 0));
                extraPoints.add(blockLoc.clone().add(0.75, 0.75, 0));
            }
            if (zMax) {
                extraPoints.add(blockLoc.clone().add(0.25, 0.25, 1));
                extraPoints.add(blockLoc.clone().add(0.25, 0.75, 1));
                extraPoints.add(blockLoc.clone().add(0.75, 0.25, 1));
                extraPoints.add(blockLoc.clone().add(0.75, 0.75, 1));
            }

            for (Location point : extraPoints) {
                if (hasDirectLineOfSight(eye, point, block)) {
                    ConsoleUtil.sendDebug("Block visible from additional point at " +
                            String.format("%.2f,%.2f,%.2f", point.getX(), point.getY(), point.getZ()));
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isOnPositiveSide(Vector point, Vector planeNormal, Vector planePoint) {
        return point.clone().subtract(planePoint).dot(planeNormal) >= 0;
    }

    private boolean isInVisionCone(Player p, Block b, Vector view, Vector[] corners) {
        Vector eye = p.getEyeLocation().toVector();
        Vector toBlock = b.getLocation().add(0.5, 0.5, 0.5)
                .toVector().subtract(eye);

        // Special handling for very close blocks
        if (toBlock.length() <= 2.0) {
            return toBlock.normalize().dot(view) > Math.cos(fieldOfView/2);
        }

        // Block is behind the player
        if (toBlock.dot(view) <= 0) return false;

        // Check if the block is inside the vision cone
        return isOnPositiveSide(toBlock.clone(), view, corners[0]) &&
                isOnPositiveSide(toBlock.clone(), view, corners[1]) &&
                isOnPositiveSide(toBlock.clone(), corners[2], view) &&
                isOnPositiveSide(toBlock.clone(), corners[3], view);
    }

    private void checkPlayerSight(Player player) {
        Vector view = calculateViewDirection(player);
        Vector[] cone = calculateVisionCone(view);
        List<Block> oreBlocks = findOreBlocksInRange(player);

        for (Block b : oreBlocks) {
            boolean inCone = isInVisionCone(player, b, view, cone);
            ConsoleUtil.sendDebug("Ore at " + b.getLocation() + " in vision cone: " + inCone);

            if (inCone) {
                boolean hasLOS = hasLineOfSight(player, b);
                ConsoleUtil.sendDebug("Has line of sight: " + hasLOS);

                if (hasLOS) {
                    OreEffect effect = oreEffects.get(b.getType());
                    if (effect != null) {
                        effect.apply(player);
                        ConsoleUtil.sendDebug("Applied " + b.getType() + " effect to " + player.getName());
                    }
                }
            }
        }
    }

    // ---------------------
    // OreEffect definitions
    // ---------------------

    private interface OreEffect { void apply(Player p); }

    private static class PotionOreEffect implements OreEffect {
        private final PotionEffectType type;
        private final int duration, amplifier;
        PotionOreEffect(PotionEffectType type, int duration, int amplifier) {
            this.type = type;
            this.duration = duration;
            this.amplifier = amplifier;
        }
        @Override public void apply(Player p) {
            p.addPotionEffect(new PotionEffect(type, duration, amplifier));
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
        @Override public void apply(Player p) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= duration) { cancel(); return; }
                    for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
                        if (e instanceof Item || e instanceof FallingBlock) {
                            Vector dir = attract
                                    ? p.getLocation().toVector().subtract(e.getLocation().toVector())
                                    : e.getLocation().toVector().subtract(p.getLocation().toVector());
                            e.setVelocity(dir.normalize().multiply(0.5));
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(
                    Reincarceration.getPlugin(Reincarceration.class), 0L, 1L
            );
        }
    }

    private static class BlockTransformOreEffect implements OreEffect {
        private final Material from, to;
        private final int radius;
        BlockTransformOreEffect(Material from, Material to, int radius) {
            this.from = from;
            this.to = to;
            this.radius = radius;
        }
        @Override public void apply(Player p) {
            Location c = p.getLocation();
            for (int x=-radius; x<=radius; x++)
                for (int y=-radius; y<=radius; y++)
                    for (int z=-radius; z<=radius; z++) {
                        Block b = c.getBlock().getRelative(x,y,z);
                        if (b.getType() == from) b.setType(to);
                    }
        }
    }

    private static class InventoryShuffleOreEffect implements OreEffect {
        @Override public void apply(Player p) {
            ItemStack[] contents = p.getInventory().getContents();
            List<ItemStack> items = new ArrayList<>();
            Map<Integer,ItemStack> keep = new HashMap<>();
            int[] armor = {36,37,38,39};
            int off = 40;
            for (int i=0; i<contents.length; i++) {
                final int finalI = i; // Create final copy for lambda
                if (Arrays.stream(armor).anyMatch(s -> s == finalI) || i == off)
                    keep.put(i, contents[i]);
                else items.add(contents[i]);
            }
            Collections.shuffle(items);
            int idx=0;
            for (int i=0; i<contents.length; i++) {
                contents[i]= keep.containsKey(i) ? keep.get(i) : items.get(idx++);
            }
            p.getInventory().setContents(contents);
            p.updateInventory();
        }
    }

    private static class BouncyBlocksOreEffect implements OreEffect {
        private final int radius, duration;
        BouncyBlocksOreEffect(int radius, int duration) {
            this.radius = radius;
            this.duration = duration;
        }
        @Override public void apply(Player p) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= duration) { cancel(); return; }
                    Location loc = p.getLocation();
                    for (int x=-radius; x<=radius; x++)
                        for (int y=-radius; y<=radius; y++)
                            for (int z=-radius; z<=radius; z++) {
                                Block b = loc.getBlock().getRelative(x,y,z);
                                if (b.getType().isSolid()) {
                                    p.getWorld().spawnParticle(
                                            Particle.ITEM_SLIME,
                                            b.getLocation().add(0.5,1,0.5),
                                            1
                                    );
                                }
                            }
                    ticks++;
                }
            }.runTaskTimer(
                    Reincarceration.getPlugin(Reincarceration.class), 0L, 1L
            );
            p.addPotionEffect(
                    new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 3)
            );
        }
    }

    private static class InventoryWeightOreEffect implements OreEffect {
        private final int duration;
        InventoryWeightOreEffect(int duration) { this.duration = duration; }
        @Override public void apply(Player p) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= duration) {
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        cancel(); return;
                    }
                    int filled = (int) Arrays.stream(
                            p.getInventory().getContents()
                    ).filter(i->i!=null&&i.getType()!=Material.AIR).count();
                    p.addPotionEffect(
                            new PotionEffect(
                                    PotionEffectType.SLOWNESS, 40, filled/9, true
                            )
                    );
                    ticks += 20;
                }
            }.runTaskTimer(
                    Reincarceration.getPlugin(Reincarceration.class), 0L, 20L
            );
        }
    }

    private static class HungerOreEffect implements OreEffect {
        private final int amount;
        HungerOreEffect(int amount) { this.amount = amount; }
        @Override public void apply(Player p) {
            p.setFoodLevel(Math.max(0, p.getFoodLevel() - amount));
        }
    }

    private static class SoundOreEffect implements OreEffect {
        private final Sound sound;
        private final float volume, pitch;
        SoundOreEffect(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
        @Override public void apply(Player p) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private class SinkingEffect implements OreEffect {
        private final Set<Material> allowed;
        private final int duration;
        private final double sinkRate;
        SinkingEffect(ConfigurationSection cfg) {
            this.allowed = cfg.getStringList("allowed_blocks").stream()
                    .map(Material::valueOf).collect(Collectors.toSet());
            this.duration = cfg.getInt("duration", 100);
            this.sinkRate = cfg.getDouble("sink_rate", 0.1);
        }
        @Override public void apply(Player p) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= duration) { cancel(); return; }
                    Location loc = p.getLocation();
                    Block below = loc.getBlock().getRelative(BlockFace.DOWN);
                    if (!allowed.contains(below.getType())) {
                        cancel(); return;
                    }
                    if (ticks == 0) {
                        loc.setX(loc.getBlockX()+0.25);
                        loc.setZ(loc.getBlockZ()+0.25);
                        p.teleport(loc);
                    }
                    p.teleport(loc.add(0, -sinkRate, 0));
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    public static class CollapseEffect implements OreEffect {
        private final Set<Material> allowed;
        private final int radius;
        CollapseEffect(ConfigurationSection cfg) {
            this.allowed = cfg.getStringList("allowed_blocks").stream()
                    .map(Material::valueOf).collect(Collectors.toSet());
            this.radius = cfg.getInt("affected_radius", 3);
        }
        @Override public void apply(Player p) {
            Location loc = p.getLocation(); int py = loc.getBlockY();
            for (int x=-radius; x<=radius; x++)
                for (int z=-radius; z<=radius; z++)
                    for (int y=1; y<=5; y++) {
                        Block b = loc.getBlock().getRelative(x, y, z);
                        if (b.getY()>py && allowed.contains(b.getType()) &&
                                b.getRelative(BlockFace.DOWN).getType().isAir()) {
                            FallingBlock fb = p.getWorld().spawnFallingBlock(
                                    b.getLocation().add(0.5,0,0.5), b.getBlockData()
                            );
                            fb.setDropItem(false);
                            fb.setHurtEntities(false);
                            b.setType(Material.AIR);
                        }
                    }
        }
    }

    private static class FireEffect implements OreEffect {
        private final int duration;
        FireEffect(ConfigurationSection cfg) {
            this.duration = cfg.getInt("duration", 100);
        }
        @Override public void apply(Player p) {
            p.setFireTicks(duration);
        }
    }

    private class ItemRepulsionEffect implements OreEffect {
        private final double radius, force;
        private final int duration;
        ItemRepulsionEffect(ConfigurationSection cfg) {
            this.radius = cfg.getDouble("radius", 5.0);
            this.force = cfg.getDouble("force", 0.5);
            this.duration = cfg.getInt("duration", 100);
        }
        @Override public void apply(Player p) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= duration) { cancel(); return; }
                    for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
                        if (e instanceof Item) {
                            Vector dir = e.getLocation().toVector()
                                    .subtract(p.getLocation().toVector());
                            e.setVelocity(dir.normalize().multiply(force));
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    private class PlayerRepulsionEffect implements OreEffect {
        private final double force;
        PlayerRepulsionEffect(ConfigurationSection cfg) {
            this.force = cfg.getDouble("force", 1.0);
        }
        @Override public void apply(Player p) {
            Block b = p.getTargetBlock(null, 5);
            Vector dir = p.getLocation().toVector()
                    .subtract(b.getLocation().toVector());
            p.setVelocity(dir.normalize().multiply(force));
        }
    }

    private class AvoidanceEffect implements OreEffect {
        private final Set<Material> allowed;
        private final int movePeriod;
        AvoidanceEffect(ConfigurationSection cfg) {
            this.allowed = cfg.getStringList("allowed_blocks").stream()
                    .map(Material::valueOf).collect(Collectors.toSet());
            this.movePeriod = cfg.getInt("move_period", 20);
        }
        @Override public void apply(Player p) {
            Block b = p.getTargetBlock(null, 5);
            if (b == null || !oreEffects.containsKey(b.getType())) {
                ConsoleUtil.sendDebug("Avoidance: no valid ore for " + p.getName());
                return;
            }
            new BukkitRunnable() {
                @Override public void run() {
                    List<Block> adj = new ArrayList<>();
                    boolean hasAir = false;
                    for (BlockFace f : BlockFace.values()) {
                        Block a = b.getRelative(f);
                        if (a.getType() == Material.AIR) { hasAir = true; break; }
                    }
                    if (!hasAir) {
                        ConsoleUtil.sendDebug("Avoidance: no adjacent air block");
                        return;
                    }
                    for (BlockFace f : BlockFace.values()) {
                        Block a = b.getRelative(f);
                        if (allowed.contains(a.getType())) adj.add(a);
                    }
                    if (adj.isEmpty()) {
                        ConsoleUtil.sendDebug("Avoidance: no valid adjacent blocks");
                        return;
                    }
                    Block target = adj.get(new Random().nextInt(adj.size()));
                    Material oreType = b.getType(), mat = target.getType();
                    target.setType(oreType);
                    b.setType(mat);
                    ConsoleUtil.sendDebug("Avoidance: swapped "
                            + b.getLocation() + " -> " + target.getLocation());
                }
            }.runTaskLater(plugin, movePeriod);
        }
    }
}