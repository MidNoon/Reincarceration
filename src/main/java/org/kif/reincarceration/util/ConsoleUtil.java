package org.kif.reincarceration.util;

import co.killionrevival.killioncommons.util.TextFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.config.ConfigManager;

public class ConsoleUtil {

    private static final ConsoleCommandSender console = Bukkit.getConsoleSender();

    private static Reincarceration plugin;
    private static ConfigManager configManager;

    public static void initialize(Reincarceration plugin) {
        ConsoleUtil.plugin = plugin;
        ConsoleUtil.configManager = plugin.getModuleManager().getConfigManager();
    }

    public static void sendFormatMessage(String message) {
        String prefix = configManager.getPrefix();
        console.sendMessage(TextFormatUtil.getComponentFromLegacyString(prefix + message));
    }

    public static void sendInfo(String message) {
        sendFormatMessage("&b " + message);
    }

    public static void sendError(String message) {
        sendFormatMessage("&c " + message);
    }

    public static void sendSuccess(String message) {
        sendFormatMessage("&2 " + message);
    }

    public static void sendDebug(String message) {
        boolean debugMode = configManager.isDebugMode();
        if (debugMode) {
            sendFormatMessage("&d " + message);
        }
    }
}