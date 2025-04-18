package org.kif.reincarceration.listener;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.modifier.core.ModifierModule;
import org.kif.reincarceration.modifier.core.ModifierManager;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.util.ConsoleUtil;
import org.kif.reincarceration.util.MessageUtil;

import java.io.File;
import java.sql.SQLException;

public class VaultAccessListener implements Listener {

    private final Reincarceration plugin;
    private final PermissionManager permissionManager;
    private final ConfigManager configManager;
    private final ModifierManager modifierManager;

    public VaultAccessListener(Reincarceration plugin) {
        this.plugin = plugin;
        this.permissionManager = new PermissionManager(plugin);
        this.configManager = plugin.getModuleManager().getConfigManager();

        ModifierModule modifierModule = plugin.getModuleManager().getModule(ModifierModule.class);
        this.modifierManager = modifierModule.getModifierManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) throws SQLException {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        Player player = event.getPlayer();
        String locationKey = getLocationKey(block);
        int vaultNumber = getChestNumberFromSign(locationKey);

        if (vaultNumber == -1) return;

        int reoffenderVaultNumber = configManager.getReoffenderVaultNumber();
        boolean isAssociated = permissionManager.isAssociatedWithBaseGroup(player.getUniqueId());

        ConsoleUtil.sendDebug("Player " + player.getName() + " attempting to access Vault #" + vaultNumber +
                ". Associated: " + isAssociated + ", Reoffender Vault: " + reoffenderVaultNumber);

        if (isAssociated) {

            IModifier activeModifier = modifierManager.getActiveModifier(player);
            if (activeModifier != null && activeModifier.handleVaultAccess(event)) {
                return;
            }

            if(vaultNumber != reoffenderVaultNumber){
                event.setCancelled(true);
                MessageUtil.sendPrefixMessage(player, "&cAccess to Regular Vault Denied.");
                ConsoleUtil.sendDebug("Blocked " + player.getName() + " from accessing Vault #" + vaultNumber);
            }
        } else if (!isAssociated && vaultNumber == reoffenderVaultNumber) {
            event.setCancelled(true);
            MessageUtil.sendPrefixMessage(player, "&cAccess to Reincarceration Vault Denied.");
            ConsoleUtil.sendDebug("Blocked " + player.getName() + " from accessing prestige Vault #" + reoffenderVaultNumber);
        }
    }

    private String getLocationKey(Block block) {
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        return world + ";;" + x + ";;" + y + ";;" + z;
    }

    private int getChestNumberFromSign(String locationKey) {
        File signsFile = new File(plugin.getDataFolder().getParentFile(), "PlayerVaults/signs.yml");
        if (!signsFile.exists()) {
            ConsoleUtil.sendError("signs.yml file not found!");
            return -1;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(signsFile);
        if (config.contains(locationKey)) {
            return config.getInt(locationKey + ".chest", -1);
        }
        ConsoleUtil.sendDebug("Chest number not found for sign at " + locationKey);
        return -1;
    }
}