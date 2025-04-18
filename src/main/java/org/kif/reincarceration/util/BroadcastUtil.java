package org.kif.reincarceration.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.config.ConfigManager;

public class BroadcastUtil {

    private static Reincarceration plugin;
    private static ConfigManager configManager;

    public static void initialize(Reincarceration plugin) {
        BroadcastUtil.plugin = plugin;
        BroadcastUtil.configManager = plugin.getModuleManager().getConfigManager();
    }

    /**
     * Broadcasts a message to all online players.
     *
     * @param message The message to broadcast
     */
    public static void broadcastMessage(String message) {
        Bukkit.getOnlinePlayers().forEach(player -> MessageUtil.sendPrefixMessage(player, message));
    }

    /**
     * Broadcasts a message to all online players with a specific permission.
     *
     * @param message The message to broadcast
     * @param permission The permission required to receive the broadcast
     */
    public static void broadcastMessageWithPermission(String message, String permission) {
       Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(permission))
                .forEach(player -> MessageUtil.sendPrefixMessage(player, message));
    }

    /**
     * Broadcasts a message to all online players except the specified player.
     *
     * @param message The message to broadcast
     * @param excludedPlayer The player to exclude from the broadcast
     */
    public static void broadcastMessageExcept(String message, Player excludedPlayer) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.equals(excludedPlayer))
                .forEach(player -> MessageUtil.sendPrefixMessage(player, message));
    }
}