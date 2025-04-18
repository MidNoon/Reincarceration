package org.kif.reincarceration.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.cycle.CycleManager;
import org.kif.reincarceration.cycle.CycleModule;
import org.kif.reincarceration.data.DataManager;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.economy.EconomyManager;
import org.kif.reincarceration.economy.EconomyModule;
import org.kif.reincarceration.gui.GUIModule;
import org.kif.reincarceration.util.MessageUtil;

public class GUICommand implements CommandExecutor {
    private final CommandModule commandModule;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final EconomyManager economyManager;
    private final CycleManager cycleManager;
    private final GUIModule guiModule;

    public GUICommand(CommandModule commandModule, ConfigManager configManager,
                      CycleModule cycleModule, DataModule dataModule, EconomyModule economyModule, GUIModule guiModule) {
        this.commandModule = commandModule;
        this.configManager = configManager;
        this.dataManager = dataModule.getDataManager();
        this.economyManager = economyModule.getEconomyManager();
        this.cycleManager = cycleModule.getCycleManager();
        this.guiModule = guiModule;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getPrefix() + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (guiModule != null) {
            guiModule.getGuiManager().openMainMenu(player);
        } else {
            MessageUtil.sendPrefixMessage(player, "&cGUI system is currently unavailable.");
        }
        return true;
    }
}