package org.kif.reincarceration.gui;

import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.core.Module;
import org.kif.reincarceration.core.CoreModule;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.cycle.CycleModule;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.economy.EconomyModule;
import org.kif.reincarceration.modifier.core.ModifierModule;
import org.kif.reincarceration.rank.RankModule;
import org.kif.reincarceration.permission.PermissionManager;
import org.kif.reincarceration.util.ConsoleUtil;

public class GUIModule implements Module {
    private final Reincarceration plugin;
    private GUIManager guiManager;

    public GUIModule(Reincarceration plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        CoreModule coreModule = plugin.getModuleManager().getModule(CoreModule.class);
        DataModule dataModule = plugin.getModuleManager().getModule(DataModule.class);
        EconomyModule economyModule = plugin.getModuleManager().getModule(EconomyModule.class);
        RankModule rankModule = plugin.getModuleManager().getModule(RankModule.class);
        ModifierModule modifierModule = plugin.getModuleManager().getModule(ModifierModule.class);
        CycleModule cycleModule = plugin.getModuleManager().getModule(CycleModule.class);

        if (coreModule == null || dataModule == null || economyModule == null || rankModule == null || modifierModule == null || cycleModule == null) {
            throw new IllegalStateException("Required modules are not initialized");
        }

        ConfigManager configManager = coreModule.getConfigManager();
        PermissionManager permissionManager = new PermissionManager(plugin);

        this.guiManager = new GUIManager(plugin, this, configManager, cycleModule.getCycleManager(),
                dataModule.getDataManager(), economyModule.getEconomyManager(), rankModule.getRankManager(),
                permissionManager, modifierModule.getModifierManager());

        ConsoleUtil.sendSuccess("GUI Module enabled");
    }

    @Override
    public void onDisable() {
        ConsoleUtil.sendSuccess("GUI Module disabled");
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public Reincarceration getPlugin() {
        return plugin;
    }
}