package org.kif.reincarceration.economy;

import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.core.Module;
import org.kif.reincarceration.util.ConsoleUtil;

public class EconomyModule implements Module {
    private final Reincarceration plugin;
    private Currency defaultCurrency;
    private EconomyManager economyManager;
    private String configuredCurrencyId;

    public EconomyModule(Reincarceration plugin) {
        this.plugin = plugin;
        this.configuredCurrencyId = plugin.getConfig().getString("economy.coins-engine.currency-id", "money");
    }

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            plugin.getLogger().severe("Disabled due to no CoinsEngine dependency found!");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        this.economyManager = new EconomyManager(this);
        ConsoleUtil.sendSuccess("Economy Module enabled with currency: " + defaultCurrency.getId());
    }

    @Override
    public void onDisable() {
        ConsoleUtil.sendSuccess("Economy Module disabled");
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("CoinsEngine") == null) {
            return false;
        }

        try {
            // Get the configured currency
            this.defaultCurrency = CoinsEngineAPI.getCurrency(configuredCurrencyId);
            if (this.defaultCurrency == null) {
                plugin.getLogger().severe("Could not find configured currency '" + configuredCurrencyId + "' in CoinsEngine!");
                // Try to get any available currency as fallback
                for (Currency currency : CoinsEngineAPI.getCurrencyManager().getCurrencies()) {
                    this.defaultCurrency = currency;
                    plugin.getLogger().warning("Using '" + currency.getId() + "' as fallback currency");
                    break;
                }
                if (this.defaultCurrency == null) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize CoinsEngine: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Currency getDefaultCurrency() {
        if (defaultCurrency == null) {
            setupEconomy();
        }
        return defaultCurrency;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public Reincarceration getPlugin() {
        return plugin;
    }
}