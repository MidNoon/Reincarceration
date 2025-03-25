package org.kif.reincarceration;

import co.killionrevival.killioncommons.util.TextFormatUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.kif.reincarceration.core.CoreModule;
import org.kif.reincarceration.core.ModuleManager;
import org.kif.reincarceration.api.ReincarcerationAPI;
import org.kif.reincarceration.api.IReincarcerationAPI;
import org.kif.reincarceration.command.CommandModule;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.economy.EconomyModule;
import org.kif.reincarceration.gui.GUIListener;
import org.kif.reincarceration.gui.GUIModule;
import org.kif.reincarceration.listener.*;
import org.kif.reincarceration.rank.RankModule;
import org.kif.reincarceration.cycle.CycleModule;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.modifier.core.ModifierModule;
import org.kif.reincarceration.rewards.RewardModule;
import org.kif.reincarceration.util.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@Getter
public class Reincarceration extends JavaPlugin implements IReincarcerationAPI {
    private ModuleManager moduleManager;
    private static final ConsoleCommandSender console = Bukkit.getConsoleSender();

    @Override
    public void onEnable() {
        try {
            // Initialize the ModuleManager
            this.moduleManager = new ModuleManager(this);

            // Register API
            this.getServer().getServicesManager().register(IReincarcerationAPI.class, this, this, ServicePriority.Normal);

            try {
                // Register modules with dependencies
                moduleManager.registerModule(new CoreModule(this));
                moduleManager.registerModule(new DataModule(this), CoreModule.class);
                moduleManager.registerModule(new EconomyModule(this), CoreModule.class, DataModule.class);
                moduleManager.registerModule(new RankModule(this), CoreModule.class, DataModule.class, EconomyModule.class);
                moduleManager.registerModule(new ModifierModule(this), CoreModule.class, DataModule.class);
                moduleManager.registerModule(new CycleModule(this), CoreModule.class, DataModule.class, EconomyModule.class,
                        RankModule.class, ModifierModule.class);
                moduleManager.registerModule(new GUIModule(this), CoreModule.class, DataModule.class, EconomyModule.class,
                        RankModule.class, ModifierModule.class, CycleModule.class);
                moduleManager.registerModule(new CommandModule(this), CoreModule.class, CycleModule.class, DataModule.class,
                        EconomyModule.class, RankModule.class, ModifierModule.class, GUIModule.class);
                moduleManager.registerModule(new RewardModule(this));

                // Enable core module
                moduleManager.enableModule(CoreModule.class);

                // Enable modules in specific order, respecting dependencies
                moduleManager.enableModule(DataModule.class);
                moduleManager.enableModule(EconomyModule.class);
                moduleManager.enableModule(RankModule.class);
                moduleManager.enableModule(ModifierModule.class);
                moduleManager.enableModule(RewardModule.class);
                moduleManager.enableModule(CycleModule.class);
                moduleManager.enableModule(GUIModule.class);
                moduleManager.enableModule(CommandModule.class);

                // Register Utilities
                BroadcastUtil.initialize(this);
                MessageUtil.initialize(this);
                ItemUtil.initialize(this);
                VaultUtil.initialize(this);
                BlockBlacklist.initialize(this);


                // Register event listeners
                // Reincarceration Player Initialization Listener
                getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

                // Reincarceration GUI Listener
                getServer().getPluginManager().registerEvents(new GUIListener(this), this);

                // Reincarceration Reward Listener
                getServer().getPluginManager().registerEvents(new RewardListener(this), this);

                // PlayerVault Interaction Listener
                getServer().getPluginManager().registerEvents(new VaultAccessListener(this), this);

                // EconomyShopGUI Transaction Listeners
                getServer().getPluginManager().registerEvents(new PreTransactionListener(this), this);
                getServer().getPluginManager().registerEvents(new PostTransactionListener(this), this);

                // -*- Reincarceration Anti-Cheat Listeners -*-
                // Flagging: items dropped by associated players breaking blocks
                getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
                // Flagging: items dropped by associated players killing entities
                getServer().getPluginManager().registerEvents(new MobDropListener(this), this);
                // Flagging: items fished by associated players
                getServer().getPluginManager().registerEvents(new FishingListener(this), this);
                // Flagging: items crafted utilizing flagged items only
                getServer().getPluginManager().registerEvents(new ItemCraftingListener(this), this);
                // Flagging: items smelted utilizing flagged items only
                getServer().getPluginManager().registerEvents(new FurnaceListener(this), this);

                getServer().getPluginManager().registerEvents(new InventoryCloseListener(this), this);

                // Prevention: Accessing Containers with unflagged items
                getServer().getPluginManager().registerEvents(new ContainerInteractionListener(this), this);
                getServer().getPluginManager().registerEvents(new EnderChestBlocker(this), this);

                getServer().getPluginManager().registerEvents(new PayCommandListener(this), this);
                getServer().getPluginManager().registerEvents(new MoneySendCommandListener(this), this);

                getServer().getPluginManager().registerEvents(new ChestShopListener(this), this);

                getServer().getPluginManager().registerEvents(new ItemPickupListener(this), this);
                getServer().getPluginManager().registerEvents(new RankupListener(this), this);

                // SimpleRegions listener
                getServer().getPluginManager().registerEvents(new SimpleRegionsListener(this), this);
                getServer().getPluginManager().registerEvents(new SweetBerryListener(this), this);

                ConsoleUtil.sendSuccess("Reincarceration has been enabled!");
            } catch (SQLException e) {
                getLogger().severe("Error during plugin initialization: " + e.getMessage());
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
            } catch (IllegalStateException e) {
                getLogger().severe("Error initializing PlayerListener: " + e.getMessage());
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
            }
        } catch (Exception e) {
            getLogger().severe("Unexpected error during plugin initialization: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        String prefix = moduleManager.getConfigManager().getPrefix();
        // Disable all modules
        if (moduleManager != null) {
            moduleManager.disableAllModules();
        }
        if (prefix != null) {
            console.sendMessage(TextFormatUtil.getComponentFromLegacyString(prefix + "&2Reincarceration has been disabled!"));
        } else {
            console.sendMessage(ChatColor.RED + "[Reincarceration] has been disabled!");
        }
    }

    // API Methods
    public boolean isPlayerInCycle(Player player) {
        return ReincarcerationAPI.isPlayerInCycle(this, player);
    }

    public IModifier getPlayerModifier(Player player) throws SQLException {
        return ReincarcerationAPI.getPlayerModifier(this, player);
    }

    public List<IModifier> getCompletedModifiers(Player player) throws SQLException {
        return ReincarcerationAPI.getCompletedModifiers(this, player);
    }

    public List<IModifier> getAvailableModifiers(Player player) throws SQLException {
        return ReincarcerationAPI.getAvailableModifiers(this, player);
    }

    public BigDecimal getStoredBalance(Player player) throws SQLException {
        return ReincarcerationAPI.getStoredBalance(this, player);
    }
}