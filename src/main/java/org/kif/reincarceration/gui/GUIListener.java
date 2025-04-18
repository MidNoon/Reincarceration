package org.kif.reincarceration.gui;

import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.api.events.PreTransactionEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.core.CoreModule;
import org.kif.reincarceration.cycle.CycleManager;
import org.kif.reincarceration.cycle.CycleModule;
import org.kif.reincarceration.modifier.core.IModifier;
import org.kif.reincarceration.modifier.core.ModifierManager;
import org.kif.reincarceration.modifier.core.ModifierModule;
import org.kif.reincarceration.rank.RankManager;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.rank.RankModule;

import java.util.ArrayList;
import java.util.List;

public class GUIListener implements Listener {
    private final Reincarceration plugin;
    private final GUIManager guiManager;
    private final CycleManager cycleManager;
    private final RankManager rankManager;
    private final ModifierManager modifierManager;
    private final ConfigManager configManager;

    public GUIListener(Reincarceration plugin) {
        this.plugin = plugin;
        GUIModule guiModule = plugin.getModuleManager().getModule(GUIModule.class);
        CoreModule coreModule = plugin.getModuleManager().getModule(CoreModule.class);
        CycleModule cycleModule = plugin.getModuleManager().getModule(CycleModule.class);
        RankModule rankModule = plugin.getModuleManager().getModule(RankModule.class);
        ModifierModule modifierModule = plugin.getModuleManager().getModule(ModifierModule.class);

        if (coreModule == null || cycleModule == null || rankModule == null || modifierModule == null) {
            throw new IllegalStateException("Required modules are not initialized");
        }

        this.guiManager = guiModule.getGuiManager();
        this.cycleManager = cycleModule.getCycleManager();
        this.rankManager = rankModule.getRankManager();
        this.modifierManager = modifierModule.getModifierManager();
        this.configManager = coreModule.getConfigManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // Cancel all events in our custom GUIs
        if (title.contains("Reincarceration") ||
                title.contains("Player Info") ||
                title.contains("Start Cycle") ||
                title.contains("Rank Up") ||
                title.contains("Modifier List") ||
                title.contains("Available Modifiers") ||
                title.contains("Completed Modifiers") ||
                title.contains("Online Players") ||
                title.contains("Quit Cycle") ||
                title.contains("Complete Cycle") ||
                title.contains("Warning: Start Cycle") ||
                title.contains("Cycle Rewards")) {
            event.setCancelled(true);

            // Prevent any item movement, even within the inventory
            if (event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY") ||
                    event.getAction().name().contains("COLLECT_TO_CURSOR")) {
                return;
            }
        }

        if (event.getCurrentItem() == null)
            return;

        if (title.startsWith(ChatColor.DARK_PURPLE + "Reincarceration Menu")) {
            handleMainMenu(player, event);
        } else if (title.startsWith(ChatColor.GOLD + "Player Info")) {
            handlePlayerInfoMenu(player, event);
        } else if (title.startsWith(ChatColor.AQUA + "Start Cycle")) {
            handleStartCycleMenu(player, event);
        } else if (title.startsWith(ChatColor.GREEN + "Rank Up")) {
            handleRankUpMenu(player, event);
        } else if (title.startsWith(ChatColor.AQUA + "Available Modifiers")) {
            handleAvailableModifiersMenu(player, event);
        } else if (title.startsWith(ChatColor.GOLD + "Completed Modifiers")) {
            handleCompletedModifiersMenu(player, event);
        } else if (title.startsWith(ChatColor.BLUE + "Online Players")) {
            handleOnlinePlayersMenu(player, event);
        } else if (title.startsWith(ChatColor.RED + "Complete Cycle")) {
            handleCompleteCycleMenu(player, event);
        } else if (title.startsWith(ChatColor.RED + "Quit Cycle")) {
            handleQuitCycleMenu(player, event);
        } else if (title.startsWith(ChatColor.RED + "Warning: Start Cycle")) {
            handleStartCycleWarningMenu(player, event);
        } else if (title.startsWith(ChatColor.GOLD + "Cycle Rewards")) {
            handleRewardsMenu(player, event);
        }
    }

    private void handleMainMenu(Player player, InventoryClickEvent event) {
        if (event.getCurrentItem().getType() == Material.BARRIER) {
            // Do nothing for disabled items
            return;
        }

        switch (event.getCurrentItem().getType()) {
            case BOOK:
                guiManager.openPlayerInfoGUI(player);
                break;
            case IRON_BARS:
                guiManager.openStartCycleGUI(player, 0);
                break;
            case EMERALD:
                guiManager.openRankUpGUI(player);
                break;
            case CARTOGRAPHY_TABLE:
                guiManager.openAvailableModifiersGUI(player, 0);
                break;
            case PLAYER_HEAD:
                guiManager.openOnlinePlayersGUI(player, 0);
                break;
            case END_CRYSTAL:
                guiManager.openCompleteCycleGUI(player);
                break;
            case LEAD:
                if (cycleManager.isPlayerInCycle(player)) {
                    guiManager.openQuitCycleGUI(player);
                }
                break;
        }
    }

    private void handlePlayerInfoMenu(Player player, InventoryClickEvent event) {
        if (event.getCurrentItem().getType() == Material.RED_WOOL) {
            guiManager.openMainMenu(player);
        }
    }

    private void handleStartCycleMenu(Player player, InventoryClickEvent event) {
        if (handleNavigationButtons(player, event, event.getView().getTitle()))
            return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
            if (clickedItem.getType() == Material.RABBIT_FOOT) {
                // Random Challenge selected
                IModifier randomModifier = createRandomModifier();
                guiManager.openStartCycleWarningGUI(player, randomModifier);
            } else if (event.isRightClick()) {
                String modifierName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                try {
                    IModifier modifier = modifierManager.getModifierByName(modifierName);
                    if (modifier != null) {
                        guiManager.openRewardItemGUI(player, modifier);
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error selecting modifier: " + e.getMessage());
                }
            } else {
                String modifierName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                try {
                    IModifier modifier = modifierManager.getModifierByName(modifierName);
                    if (modifier != null) {
                        guiManager.openStartCycleWarningGUI(player, modifier);
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error selecting modifier: " + e.getMessage());
                }
            }
        }
    }

    private void handleRewardsMenu(Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null)
            return;
        if (clickedItem.getType() != Material.REDSTONE_BLOCK)
            return;
        guiManager.openMainMenu(player);
    }

    private void handleStartCycleWarningMenu(Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null)
            return;

        if (clickedItem.getType() == Material.EMERALD_BLOCK) {
            // Confirm start cycle
            ItemStack modifierItem = event.getInventory().getItem(13);
            if (modifierItem != null && modifierItem.hasItemMeta() && modifierItem.getItemMeta().hasLore()) {
                String modifierName = ChatColor.stripColor(modifierItem.getItemMeta().getLore().get(2));
                modifierName = modifierName.substring(modifierName.lastIndexOf(":") + 2);
                try {
                    IModifier modifier;
                    if ("Random Challenge".equals(modifierName)) {
                        // Use the placeholder random modifier
                        modifier = createRandomModifier();
                    } else {
                        modifier = modifierManager.getModifierByName(modifierName);
                    }

                    if (modifier != null) {
                        cycleManager.startNewCycle(player, modifier);
                        player.closeInventory();
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Error starting cycle: " + e.getMessage());
                }
            }
        } else if (clickedItem.getType() == Material.REDSTONE_BLOCK) {
            // Cancel and return to main menu
            guiManager.openMainMenu(player);
        }
    }

    private void handleRankUpMenu(Player player, InventoryClickEvent event) {
        if (event.getCurrentItem().getType() == Material.RED_WOOL) {
            guiManager.openMainMenu(player);
        } else if (event.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
            try {
                if (rankManager.canRankUp(player)) {
                    rankManager.rankUp(player);
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "You've successfully ranked up!");
                    guiManager.openRankUpGUI(player); // Reopen the GUI to show updated info
                } else {
                    player.sendMessage(ChatColor.RED + "You can't rank up right now.");
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Error ranking up: " + e.getMessage());
            }
        }
    }

    private void handleModifierListMenu(Player player, InventoryClickEvent event) {
        handleNavigationButtons(player, event, event.getView().getTitle());
    }

    private void handleQuitCycleMenu(Player player, InventoryClickEvent event) {
        switch (event.getCurrentItem().getType()) {
            case EMERALD_BLOCK:
                cycleManager.quitCycle(player);
                player.closeInventory();
                break;
            case REDSTONE_BLOCK:
                guiManager.openMainMenu(player);
                break;
        }
    }

    private void handleOnlinePlayersMenu(Player player, InventoryClickEvent event) {
        handleNavigationButtons(player, event, event.getView().getTitle());
    }

    private void handleCompleteCycleMenu(Player player, InventoryClickEvent event) {
        if (event.getCurrentItem().getType() == Material.RED_WOOL) {
            guiManager.openMainMenu(player);
        } else if (event.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
            try {
                if (cycleManager.isPlayerInCycle(player)
                        && configManager.isMaxRank(rankManager.getPlayerRank(player))) {
                    cycleManager.completeCycle(player);
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "You've successfully completed your cycle!");
                    guiManager.openMainMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You can't complete the cycle right now.");
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Error completing cycle: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleAvailableModifiersMenu(Player player, InventoryClickEvent event) {
        if (event.getCurrentItem() == null)
            return;

        if (event.getCurrentItem().getType() == Material.BOOK) {
            guiManager.openCompletedModifiersGUI(player, 0);
        } else if (event.isRightClick()) {
            String modifierName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
            try {
                IModifier modifier = modifierManager.getModifierByName(modifierName);
                if (modifier != null) {
                    guiManager.openRewardItemGUI(player, modifier);
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Error selecting modifier: " + e.getMessage());
            }
        } else {
            handleNavigationButtons(player, event, event.getView().getTitle());
        }
    }

    private void handleCompletedModifiersMenu(Player player, InventoryClickEvent event) {
        if (event.getCurrentItem().getType() == Material.CARTOGRAPHY_TABLE) {
            guiManager.openAvailableModifiersGUI(player, 0);
        } else {
            handleNavigationButtons(player, event, event.getView().getTitle());
        }
    }

    private boolean handleNavigationButtons(Player player, InventoryClickEvent event, String guiName) {
        if (event.getCurrentItem().getType() == Material.RED_WOOL) {
            guiManager.openMainMenu(player);
            return true;
        } else if (event.getCurrentItem().getType() == Material.ARROW) {
            String pageStr = guiName.substring(guiName.lastIndexOf("Page ") + 5, guiName.length() - 1);
            int currentPage = Integer.parseInt(pageStr) - 1;

            if (event.getCurrentItem().getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Next Page")) {
                openNextPage(player, guiName, currentPage);
                return true;
            } else if (event.getCurrentItem().getItemMeta().getDisplayName()
                    .equals(ChatColor.GREEN + "Previous Page")) {
                openPreviousPage(player, guiName, currentPage);
                return true;
            }
        }
        return false;
    }

    private void openNextPage(Player player, String guiName, int currentPage) {
        if (guiName.startsWith(ChatColor.AQUA + "Start Cycle")) {
            guiManager.openStartCycleGUI(player, currentPage + 1);
        } else if (guiName.startsWith(ChatColor.AQUA + "Available Modifiers")) {
            guiManager.openAvailableModifiersGUI(player, currentPage + 1);
        } else if (guiName.startsWith(ChatColor.GOLD + "Completed Modifiers")) {
            guiManager.openCompletedModifiersGUI(player, currentPage + 1);
        } else if (guiName.startsWith(ChatColor.BLUE + "Online Players")) {
            guiManager.openOnlinePlayersGUI(player, currentPage + 1);
        }
    }

    private void openPreviousPage(Player player, String guiName, int currentPage) {
        if (guiName.startsWith(ChatColor.AQUA + "Start Cycle")) {
            guiManager.openStartCycleGUI(player, currentPage - 1);
        } else if (guiName.startsWith(ChatColor.AQUA + "Available Modifiers")) {
            guiManager.openAvailableModifiersGUI(player, currentPage - 1);
        } else if (guiName.startsWith(ChatColor.GOLD + "Completed Modifiers")) {
            guiManager.openCompletedModifiersGUI(player, currentPage - 1);
        } else if (guiName.startsWith(ChatColor.BLUE + "Online Players")) {
            guiManager.openOnlinePlayersGUI(player, currentPage - 1);
        }
    }

    private IModifier createRandomModifier() {
        return new IModifier() {
            @Override
            public String getId() {
                return "random";
            }

            @Override
            public String getName() {
                return "Random Challenge";
            }

            @Override
            public String getDescription() {
                return "A randomly selected challenge";
            }

            @Override
            public List<ItemStack> getItemRewards() {
                return new ArrayList<>();
            }

            @Override
            public void apply(Player player) {
            } // Empty implementation

            @Override
            public void remove(Player player) {
            } // Empty implementation

            @Override
            public boolean isActive(Player player) {
                return false;
            } // Always return false for the placeholder

            @Override
            public boolean isSecret() {
                return false;
            }

            @Override
            public boolean handleBlockBreak(BlockBreakEvent event) {
                return false;
            }

            @Override
            public boolean handleFishing(PlayerFishEvent event) {
                return false;
            }

            @Override
            public boolean handleSellTransaction(PreTransactionEvent event) {
                return false;
            }

            @Override
            public boolean handleBuyTransaction(PreTransactionEvent event) {
                return false;
            }

            @Override
            public boolean handlePostTransaction(PostTransactionEvent event) {
                return false;
            }

            @Override
            public boolean handleVaultAccess(PlayerInteractEvent event) {
                return false;
            }
        };
    }
}