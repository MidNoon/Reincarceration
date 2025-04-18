package org.kif.reincarceration.modifier.types;

import me.gypopo.economyshopgui.api.events.PreTransactionEvent;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.Transaction;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.kif.reincarceration.util.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class AnglerModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;
    // Separated config settings for clarity
    private boolean provideRodOnApply, provideRodOnDeath, preventRodDurabilityLoss;
    private final Set<Material> allowedItems = new HashSet<>();
    private final Map<Material,Integer> disallowedSwapItems = new EnumMap<>(Material.class);
    private final Map<UUID,BukkitRunnable> activeWaterTasks = new HashMap<>();
    private final Map<UUID,GlowSquid> activeSquids = new HashMap<>();
    private final Map<UUID,FishingData> playerFishingData = new HashMap<>();
    private final Map<UUID,Boolean> playerInWaterStatus = new HashMap<>();

    private double pushForce, squidSpeed, fishingPullForce,
            safeguardUpwardForce, safeguardHorizontalForce,
            minSquidSpawnDistance, maxSquidSpawnDistance;
    private int pushInterval, minWaterSize,
            fishingPullDuration, safeguardThreshold;

    private static class FishingData {
        Vector waterDirection;
        int unchangedCount;
        double lastCoord;
        FishingData(Vector dir, double coord) {
            this.waterDirection = dir;
            this.unchangedCount = 0;
            this.lastCoord = coord;
        }
    }

    public AnglerModifier(Reincarceration plugin) {
        super("angler","Angler",
                "Fishing perks, sell restrictions, water hazards, pull effects");
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection cfg = plugin.getConfig()
                .getConfigurationSection("modifiers.angler");
        if (cfg == null) {
            ConsoleUtil.sendError("Angler config not found. Using defaults.");
            provideRodOnApply = true;
            provideRodOnDeath = true;
            preventRodDurabilityLoss = true;
            pushForce = 0.5;
            pushInterval = 20;
            minWaterSize = 50;
            squidSpeed = 0.5;
            fishingPullForce = 0.05;
            fishingPullDuration = 20;
            safeguardThreshold = 5;
            safeguardUpwardForce = 1.0;
            safeguardHorizontalForce = 1.5;
            minSquidSpawnDistance = 5.0;
            maxSquidSpawnDistance = 15.0;
            initDefaultAllowed();
            initDefaultSwaps();
        } else {
            // Added separate setting for initial rod provision
            provideRodOnApply = cfg.getBoolean("provide_rod_on_apply", true);
            provideRodOnDeath = cfg.getBoolean("provide_rod_on_death", true);
            preventRodDurabilityLoss = cfg.getBoolean("prevent_rod_durability_loss", true);
            pushForce = cfg.getDouble("push_force", 0.5);
            pushInterval = cfg.getInt("push_interval", 20);
            minWaterSize = cfg.getInt("min_water_size", 50);
            squidSpeed = cfg.getDouble("squid_speed", 0.5);
            fishingPullForce = cfg.getDouble("fishing_pull_force", 0.05);
            fishingPullDuration = cfg.getInt("fishing_pull_duration", 20);
            safeguardThreshold = cfg.getInt("safeguard_threshold", 5);
            safeguardUpwardForce = cfg.getDouble("safeguard_upward_force",1.0);
            safeguardHorizontalForce = cfg.getDouble("safeguard_horizontal_force",1.5);
            minSquidSpawnDistance = cfg.getDouble("min_squid_spawn_distance",5.0);
            maxSquidSpawnDistance = cfg.getDouble("max_squid_spawn_distance",15.0);
            List<String> allow = cfg.getStringList("allowed_items");
            if (!allow.isEmpty()) for (String s:allow) try {
                allowedItems.add(Material.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException e) {
                ConsoleUtil.sendError("Invalid allow item: " + s);
            } else initDefaultAllowed();
            List<String> swaps = cfg.getStringList("disallowed_swap_items");
            if (!swaps.isEmpty()) try {
                for (String s:swaps) {
                    String[] p = s.split(" ");
                    disallowedSwapItems.put(
                            Material.valueOf(p[0].toUpperCase()),
                            Integer.parseInt(p[1])
                    );
                }
            } catch (Exception e) {
                ConsoleUtil.sendError("Invalid swap item config.");
                initDefaultSwaps();
            } else initDefaultSwaps();

            ConsoleUtil.sendDebug("Angler config loaded: allowed="+allowedItems+" swaps="+disallowedSwapItems);
        }
    }

    private void initDefaultAllowed() {
        Collections.addAll(allowedItems,
                Material.COD,Material.SALMON,Material.TROPICAL_FISH,
                Material.PUFFERFISH,Material.NAUTILUS_SHELL,
                Material.FISHING_ROD,Material.ENCHANTED_BOOK,
                Material.BOW,Material.LILY_PAD,Material.BOWL,
                Material.LEATHER,Material.LEATHER_BOOTS,
                Material.SADDLE,Material.NAME_TAG,
                Material.TRIPWIRE_HOOK,Material.STICK,
                Material.INK_SAC,Material.BAMBOO,
                Material.COOKED_COD,Material.COOKED_SALMON
        );
    }

    private void initDefaultSwaps() {
        disallowedSwapItems.put(Material.COD,60);
        disallowedSwapItems.put(Material.SALMON,25);
        disallowedSwapItems.put(Material.TROPICAL_FISH,2);
        disallowedSwapItems.put(Material.PUFFERFISH,13);
    }

    @Override
    public void apply(Player p) {
        super.apply(p);
        // Now using the correct setting for initial application
        if (p.isOnline() && !hasRod(p) && provideRodOnApply) giveRod(p);
        ConsoleUtil.sendDebug("Angler applied to " + p.getName());
    }

    @Override
    public void remove(Player p) {
        super.remove(p);
        stopWaterTask(p);
        playerInWaterStatus.remove(p.getUniqueId());
        ConsoleUtil.sendDebug("Angler removed from " + p.getName());
    }

    private boolean hasRod(Player p) {
        return Arrays.stream(p.getInventory().getContents())
                .anyMatch(i->i!=null&&i.getType()==Material.FISHING_ROD);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p=e.getPlayer();
        // Using the correct setting for respawn
        if (isActive(p) && provideRodOnDeath) {
            new BukkitRunnable() { public void run(){ giveRod(p); } }
                    .runTaskLater(plugin,5L);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player p=e.getPlayer(); if (!isActive(p)) return;
        if (e.getState()==PlayerFishEvent.State.CAUGHT_FISH ||
                e.getState()==PlayerFishEvent.State.CAUGHT_ENTITY) {
            Entity c=e.getCaught();
            if (c instanceof Item) {
                Item caughtItem = (Item)c;
                ItemStack stack = caughtItem.getItemStack();
                if (!allowedItems.contains(stack.getType())) {
                    Material randomFish = getRandomFish();
                    ItemStack newItem = new ItemStack(randomFish);
                    ItemUtil.addReincarcerationFlag(newItem);
                    caughtItem.setItemStack(newItem);
                    ConsoleUtil.sendDebug("Replaced catch with " + randomFish);
                }
            }
            handleFishingPull(p);
        }
        if (preventRodDurabilityLoss) restoreRod(p);
    }

    private void restoreRod(Player p) {
        ItemStack rod = p.getInventory().getItemInMainHand();
        if (rod.getType() == Material.FISHING_ROD) {
            ItemMeta meta = rod.getItemMeta();
            if (meta == null) return;

            // Skip enchanted rods
            if (meta.hasEnchants()) return;

            if (meta instanceof Damageable dmg) {
                dmg.setDamage(50);
                rod.setItemMeta(meta);
                ConsoleUtil.sendDebug("Restored rod for " + p.getName());
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p=e.getPlayer(); if (!isActive(p)) return;
        boolean was = playerInWaterStatus.getOrDefault(p.getUniqueId(),false);
        boolean now = p.getLocation().getBlock().getType()==Material.WATER;
        if (!was && now) {
            playerInWaterStatus.put(p.getUniqueId(), true);
            startWaterTask(p);
        } else if (was && !inWater(p)) {
            playerInWaterStatus.put(p.getUniqueId(),false);
        }
    }

    private boolean inWater(Player p) {
        Block b=p.getLocation().getBlock();
        return b.getType()==Material.WATER || b.getRelative(BlockFace.DOWN).getType()==Material.WATER;
    }

    private void handleFishingPull(Player p) {
        Vector dir = findNearestWaterDirection(p.getLocation());
        if (dir==null) return;
        FishingData fd=playerFishingData.computeIfAbsent(
                p.getUniqueId(), k->new FishingData(dir,getCoord(p,dir))
        );
        double curr=getCoord(p,dir);
        if (Math.abs(curr-fd.lastCoord)<0.01) fd.unchangedCount++;
        else fd.unchangedCount=0;
        fd.lastCoord=curr;
        if (fd.unchangedCount>=safeguardThreshold) applySafeguard(p,dir);
        else applyPull(p,dir);
    }

    private double getCoord(Player p, Vector dir) {
        Location l=p.getLocation(); return Math.abs(dir.getX())>Math.abs(dir.getZ())
                ? l.getX() : l.getZ();
    }

    private void applySafeguard(Player p, Vector dir) {
        p.setVelocity(new Vector(0,safeguardUpwardForce,0));
        new BukkitRunnable(){ public void run(){
            p.setVelocity(dir.multiply(safeguardHorizontalForce));
        }}.runTaskLater(plugin,3L);
        playerFishingData.remove(p.getUniqueId());
    }

    private void applyPull(Player p, Vector dir) {
        new BukkitRunnable(){ int t=0;
            public void run(){
                if (!p.isOnline()||!isActive(p)||t>=fishingPullDuration) { cancel(); return; }
                p.setVelocity(p.getVelocity().add(dir.multiply(fishingPullForce)));
                t++;
            }
        }.runTaskTimer(plugin,0L,1L);
    }

    private void startWaterTask(Player p) {
        if (activeWaterTasks.containsKey(p.getUniqueId())) return;
        BukkitRunnable task=new BukkitRunnable(){ int oow=0;
            public void run(){
                if (!p.isOnline()||!isActive(p)) { stopWaterTask(p); return; }
                if (!inWater(p)) {
                    if (++oow>10) { stopWaterTask(p); return; }
                } else {
                    oow=0;
                    pushPlayerAway(p);
                    if (isLargeWaterBody(p.getLocation()) && !activeSquids.containsKey(p.getUniqueId())) spawnSquid(p);
                }
            }
        };
        task.runTaskTimer(plugin,0L,pushInterval);
        activeWaterTasks.put(p.getUniqueId(),task);
    }

    private void stopWaterTask(Player p) {
        BukkitRunnable t=activeWaterTasks.remove(p.getUniqueId());
        if (t!=null) { t.cancel(); removeSquid(p); playerInWaterStatus.put(p.getUniqueId(),false);
            ConsoleUtil.sendDebug("Stopped water task for " + p.getName()); }
    }

    private void pushPlayerAway(Player p) {
        Vector dir=new Vector(Math.random()-0.5,-0.2,Math.random()-0.5)
                .normalize().multiply(pushForce);
        p.setVelocity(p.getVelocity().add(dir));
    }

    private boolean isLargeWaterBody(Location loc) {
        int count=0;
        for (int x=-5;x<=5;x++) for (int y=-5;y<=5;y++) for (int z=-5;z<=5;z++) {
            if (loc.getBlock().getRelative(x,y,z).getType()==Material.WATER
                    && ++count>=minWaterSize) return true;
        }
        return false;
    }

    private Location findWaterBlockNearby(World w,double x,int y,double z){
        for(int d=0;d<=10;d++){for(int s: new int[]{1, -1}){
            int yy=y+s*d;
            if(yy>0&&yy<w.getMaxHeight()){
                Location L=new Location(w,x,yy,z);
                if(isValidSquidSpawnLocation(L))return L;
            }
        }}
        return null;
    }

    private boolean isValidSquidSpawnLocation(Location loc) {
        return loc.getBlock().getType()==Material.WATER
                && loc.getBlock().getRelative(BlockFace.UP).getType()==Material.WATER
                && loc.getBlock().getRelative(BlockFace.DOWN).getType()!=Material.AIR;
    }

    private void spawnSquid(Player p) {
        Location s=findWaterSpawnLocation(p.getLocation());
        if(s!=null){
            GlowSquid sq=p.getWorld().spawn(s,GlowSquid.class);
            activeSquids.put(p.getUniqueId(),sq);
            pursuePlayer(sq,p);
            ConsoleUtil.sendDebug("Spawned squid for " + p.getName() + " at " + s);
        } else ConsoleUtil.sendDebug("No spawn loc for squid");
    }

    private Location findWaterSpawnLocation(Location pl) {
        World w=pl.getWorld(); if(w==null)return null;
        for(int i=0;i<50;i++){
            double dist=minSquidSpawnDistance+Math.random()*(maxSquidSpawnDistance-minSquidSpawnDistance);
            double ang=Math.random()*2*Math.PI;
            double x=pl.getX()+dist*Math.cos(ang),
                    z=pl.getZ()+dist*Math.sin(ang);
            Location loc=findWaterBlockNearby(w,x,pl.getBlockY(),z);
            if(loc!=null) return loc;
        }
        return null;
    }

    private void pursuePlayer(GlowSquid sq, Player p) {
        new BukkitRunnable(){ int t=0,oow=0;
            public void run(){
                if(!sq.isValid()||!p.isOnline()||!isActive(p)){
                    removeSquid(p); cancel(); return;
                }
                if(!inWater(p)){
                    if(++oow>40){ removeSquid(p); cancel(); return; }
                } else {
                    oow=0;
                    Vector dir=p.getLocation().toVector().subtract(sq.getLocation().toVector())
                            .normalize().multiply(squidSpeed);
                    sq.setVelocity(dir);
                    if(sq.getLocation().distance(p.getLocation())<1.5){
                        p.setHealth(0); removeSquid(p); cancel(); return;
                    }
                }
                if(++t>=20*60){ removeSquid(p); cancel(); }
            }
        }.runTaskTimer(plugin,0L,1L);
    }

    private void removeSquid(Player p) {
        GlowSquid sq=activeSquids.remove(p.getUniqueId()); if(sq!=null) sq.remove();
    }

    private Vector findNearestWaterDirection(Location loc) {
        World w=loc.getWorld(); if(w==null)return null;
        int r=5; Vector pos=loc.toVector(), nearest=null; double minDist=Double.MAX_VALUE;
        for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++)for(int z=-r;z<=r;z++){
            Block b=w.getBlockAt(loc.getBlockX()+x,loc.getBlockY()+y,loc.getBlockZ()+z);
            if(b.getType()==Material.WATER) {
                Vector v=b.getLocation().toVector();
                double d=v.distanceSquared(pos);
                if(d<minDist){minDist=d;nearest=v;}
            }
        }
        return nearest!=null?nearest.subtract(pos).normalize():null;
    }

    @Override
    public boolean handleSellTransaction(PreTransactionEvent e) {
        Player p=e.getPlayer(); ShopItem si=e.getShopItem();
        if(si!=null&&si.getItemToGive()!=null){
            Material m=si.getItemToGive().getType();
            ConsoleUtil.sendDebug("Sell check " + m);
            if(!allowedItems.contains(m)){
                e.setCancelled(true);
                MessageUtil.sendPrefixMessage(p,"&cTransaction Denied - prohibited item.");
                ConsoleUtil.sendDebug("Denied sell of " + m);
                return true;
            }
        }
        return false;
    }

    private void giveRod(Player p) {
        if(!p.getInventory().contains(Material.FISHING_ROD)){
            ItemStack it=new ItemStack(Material.FISHING_ROD);
            ItemMeta meta=it.getItemMeta();
            if(meta instanceof Damageable dmg) dmg.setDamage(50);
            it.setItemMeta(meta);
            ItemUtil.addReincarcerationFlag(it);
            p.getInventory().addItem(it);
            ConsoleUtil.sendDebug("Gave rod to " + p.getName());
        }
    }

    private Material getRandomFish() {
        int total=disallowedSwapItems.values().stream().mapToInt(i->i).sum();
        int r=ThreadLocalRandom.current().nextInt(total), cum=0;
        for(Map.Entry<Material,Integer> en:disallowedSwapItems.entrySet()){
            cum+=en.getValue();
            if(r<cum) return en.getKey();
        }
        return Material.COD;
    }

    public void reloadConfig() { loadConfig(); }
}