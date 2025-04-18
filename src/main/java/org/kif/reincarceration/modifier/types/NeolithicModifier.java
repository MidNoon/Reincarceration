package org.kif.reincarceration.modifier.types;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.kif.reincarceration.util.BlockBreakUtil;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.MessageUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class NeolithicModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;
    private final Set<Material> allowedTools;
    private final Map<UUID, BukkitTask> hasteTasks = new HashMap<>();

    public NeolithicModifier(Reincarceration plugin) {
        super("neolithic", "Neolithic", "Limits players to basic tools");
        this.plugin = plugin;

        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modifiers.neolithic");
        assert config != null;
        this.allowedTools = loadMaterialSet(config, "allowed_tools");
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        startHasteEffect(player);
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        stopHasteEffect(player);
    }

    private void startHasteEffect(Player player) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            applyHasteEffect(player);
        }, 0L, 20L * 30); // Run every 30 seconds
        hasteTasks.put(player.getUniqueId(), task);
    }

    private void stopHasteEffect(Player player) {
        BukkitTask task = hasteTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        player.removePotionEffect(PotionEffectType.HASTE);
    }

    private void applyHasteEffect(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 40, 0, false, false));
    }

    @Override
    public boolean handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Neolithic-specific check: only allow basic tools
        if (!allowedTools.contains(itemInHand.getType()) && isTool(itemInHand.getType())) {
            event.setCancelled(true);
            MessageUtil.sendPrefixMessage(player, "&cYou can only use basic tools!");
            ConsoleUtil.sendDebug(player.getName() + " attempted to use " + itemInHand.getType() +
                                " but was prevented by Neolithic Modifier");
            return true;
        }

        // Use the shared block break handling utility
        return BlockBreakUtil.handleBlockBreak(event, player, event.getBlock(), plugin);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        if (!isActive(player)) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!allowedTools.contains(itemInHand.getType()) && isTool(itemInHand.getType())) {
            event.setCancelled(true);
            MessageUtil.sendPrefixMessage(player, "&cYou can only deal damage with basic tools!");
            ConsoleUtil.sendDebug(player.getName() + " attempted to deal damage with " +
                               itemInHand.getType() + " but was prevented by Neolithic Modifier");
        }
    }

    private boolean isTool(Material material) {
        return material.name().endsWith("_PICKAXE") || material.name().endsWith("_AXE") ||
                material.name().endsWith("_SHOVEL") || material.name().endsWith("_HOE") ||
                material.name().endsWith("_SWORD");
    }

    private Set<Material> loadMaterialSet(ConfigurationSection config, String path) {
        Set<Material> materials = new HashSet<>();
        List<String> materialNames = config.getStringList(path);
        for (String name : materialNames) {
            Material material = Material.getMaterial(name.toUpperCase());
            if (material != null) {
                materials.add(material);
            } else {
                ConsoleUtil.sendError("Invalid material in neolithic config: " + name);
            }
        }
        return materials;
    }
}