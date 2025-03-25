package org.kif.reincarceration.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.ItemUtil;

import java.util.Random;

public class SweetBerryListener implements Listener {
    private final Reincarceration plugin;
    private final PermissionManager permissionManager;
    private final Random random = new Random();

    public SweetBerryListener(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBerryPicking(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Early exit if player is not associated
        boolean isAssociated = permissionManager.isAssociatedWithBaseGroup(player.getUniqueId());
        if (!isAssociated) {
            return;
        }

        // Check if the player is right-clicking a sweet berry bush
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SWEET_BERRY_BUSH) {
            return;
        }

        ConsoleUtil.sendDebug("Reincarcerated player " + player.getName() + " is picking sweet berries");

        // Cancel the default event
        event.setCancelled(true);

        // Manually handle the berry picking
        handleBerryPicking(block, player);
    }

    private void handleBerryPicking(Block block, Player player) {
        // Check if the bush has berries (age 2 or 3)
        if (block.getBlockData() instanceof Ageable) {
            Ageable berryBush = (Ageable) block.getBlockData();
            int age = berryBush.getAge();

            if (age >= 2) { // Mature bush with berries
                // Reduce age to 1 (picked bush)
                berryBush.setAge(1);
                block.setBlockData(berryBush);

                // Determine number of berries to drop (1-3 for age 2, 1-4 for age 3)
                int berryCount = age == 2 ? random.nextInt(3) + 1 : random.nextInt(4) + 1;

                // Create flagged berries
                ItemStack berries = new ItemStack(Material.SWEET_BERRIES, berryCount);
                ItemUtil.addReincarcerationFlag(berries);

                // Drop the berries at the bush location
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), berries);

                // Play the berry picking sound
                player.playSound(block.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.0f);

                // Apply damage to the player as vanilla would (half a heart)
                player.damage(1.0);

                ConsoleUtil.sendDebug("Dropped " + berryCount + " flagged sweet berries for player: " + player.getName());
            }
        }
    }
}