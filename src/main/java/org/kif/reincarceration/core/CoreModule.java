package org.kif.reincarceration.core;

import lombok.Getter;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.util.ConsoleUtil;

public class CoreModule implements Module {
    private final Reincarceration plugin;
    @Getter
    private final ConfigManager configManager;

    public CoreModule(Reincarceration plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.configManager = new ConfigManager(plugin, plugin.getConfig());
    }

    @Override
    public void onEnable() {
        ConsoleUtil.initialize(this.plugin);
        ConsoleUtil.sendSuccess("Core Module enabled");
    }

    @Override
    public void onDisable() {
        ConsoleUtil.sendSuccess("Core Module disabled");
    }
}