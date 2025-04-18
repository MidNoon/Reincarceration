package org.kif.reincarceration.modifier.types;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.cycle.CycleManager;
import org.kif.reincarceration.cycle.CycleModule;
import org.kif.reincarceration.modifier.core.AbstractModifier;
import org.kif.reincarceration.util.ConsoleUtil;

public class HardcoreModifier extends AbstractModifier implements Listener {
    private final Reincarceration plugin;

    public HardcoreModifier(Reincarceration plugin) {
        super("hardcore", "Hardcore", "One death means game over!");
        this.plugin = plugin;
        ConsoleUtil.sendDebug("HardcoreModifier initialized");
    }

    @Override
    public void apply(Player player) {
        super.apply(player);
        ConsoleUtil.sendDebug("Applied Hardcore modifier to " + player.getName());
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        ConsoleUtil.sendDebug("Removed Hardcore modifier from " + player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            ConsoleUtil.sendDebug("Dead entity is a player: " + player.getName());

            boolean isActive = isActive(player);
            ConsoleUtil.sendDebug("Is Hardcore modifier active for " + player.getName() + ": " + isActive);

            if (isActive) {
                ConsoleUtil.sendDebug("Player " + player.getName() + " died with Hardcore modifier active. Attempting to quit cycle.");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    ConsoleUtil.sendDebug("Executing quitCycle for " + player.getName());
                    CycleManager cycleManager = getCycleManager();
                    if (cycleManager != null) {
                        cycleManager.quitCycle(player);
                        ConsoleUtil.sendDebug("quitCycle execution completed for " + player.getName());
                    } else {
                        ConsoleUtil.sendError("CycleManager is null. Cannot quit cycle for " + player.getName());
                    }
                });
            } else {
                ConsoleUtil.sendDebug("Hardcore modifier not active for " + player.getName() + ". No action taken.");
            }
        }
    }

    private CycleManager getCycleManager() {
        CycleModule cycleModule = plugin.getModuleManager().getModule(CycleModule.class);
        return cycleModule != null ? cycleModule.getCycleManager() : null;
    }
}