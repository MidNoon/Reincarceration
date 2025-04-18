package org.kif.reincarceration.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.data.DataManager;
import org.kif.reincarceration.data.DataModule;
import org.kif.reincarceration.util.ConsoleUtil;
import com.flyerzrule.mc.customtags.api.CustomTagsAPI;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class PermissionManager {
    private final Reincarceration plugin;
    private final ConfigManager configManager;
    private final LuckPerms luckPerms;
    private final DataManager dataManager;
    private CustomTagsAPI customTagsAPI;

    public PermissionManager(Reincarceration plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getModuleManager().getConfigManager();
        this.luckPerms = plugin.getServer().getServicesManager().load(LuckPerms.class);
        DataModule dataModule = plugin.getModuleManager().getModule(DataModule.class);
        this.dataManager = dataModule.getDataManager();

        try {
            this.customTagsAPI = plugin.getServer()
                    .getServicesManager().getRegistration(CustomTagsAPI.class).getProvider();
        } catch (Exception e) {
            this.customTagsAPI = null;
            ConsoleUtil.sendError("Failed to load CustomTagsAPI: " + e.getMessage());
        }
    }

    public void updatePlayerRankGroup(Player player, int rank) {
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms not found! Unable to update player rank group.");
            return;
        }

        String groupName = configManager.getRankPermissionGroup(rank);
        String entryGroup = configManager.getEntryGroup();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());

        if (user == null) {
            plugin.getLogger().severe("Unable to get LuckPerms user for " + player.getName());
            return;
        }

        // Remove from entry group
        user.data().remove(InheritanceNode.builder(entryGroup).build());

        // Remove old rank groups
        user.data().clear(node -> node.getKey().startsWith("group.reoffender_"));

        // Add new rank group
        InheritanceNode groupNode = InheritanceNode.builder(groupName).build();
        user.data().add(groupNode);

        // Set primary group
        user.setPrimaryGroup(groupName);

        // default group
        user.data().clear(node -> node.getKey().equals("group.default"));

        // Save changes
        luckPerms.getUserManager().saveUser(user);
    }

    public void resetToDefaultGroup(Player player) {
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms not found! Unable to reset player group.");
            return;
        }

        String entryGroup = configManager.getEntryGroup();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());

        if (user == null) {
            plugin.getLogger().severe("Unable to get LuckPerms user for " + player.getName());
            return;
        }

        // Remove all reoffender rank groups
        user.data().clear(node -> node.getKey().startsWith("group.reoffender_"));

        // Add back to entry group
        InheritanceNode groupNode = InheritanceNode.builder(entryGroup).build();
        user.data().add(groupNode);

        // Set primary group back to entry
        user.setPrimaryGroup(entryGroup);

        // default group
        user.data().clear(node -> node.getKey().equals("group.default"));

        // Remove default group
        // user.data().remove(InheritanceNode.builder("default").build());

        addCompletionPrefix(player);

        // Save changes
        luckPerms.getUserManager().saveUser(user);
    }

    public void addPermission(Player player, String permission) {
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms not found! Unable to add permission.");
            return;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            plugin.getLogger().severe("Unable to get LuckPerms user for " + player.getName());
            return;
        }

        user.data().add(Node.builder(permission).build());
        luckPerms.getUserManager().saveUser(user);
    }

    public void removePermission(Player player, String permission) {
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms not found! Unable to remove permission.");
            return;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            plugin.getLogger().severe("Unable to get LuckPerms user for " + player.getName());
            return;
        }

        user.data().remove(Node.builder(permission).build());
        luckPerms.getUserManager().saveUser(user);
    }

    public boolean hasPermission(Player player, String permission) {
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms not found! Unable to check permission.");
            return false;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            plugin.getLogger().severe("Unable to get LuckPerms user for " + player.getName());
            return false;
        }

        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    public void addCompletionPrefix(Player player) {
        if (customTagsAPI == null) {
            ConsoleUtil.sendError(
                    "CustomTagsAPI is not available. Completion prefixes cannot be added. Use /rcreloadtags to reload tags for player: "
                            + player.getDisplayName());
            return;
        }
        try {
            int cycleCount = dataManager.getPlayerCycleCount(player);
            List<String> completedModifiers = dataManager.getCompletedModifiers(player);

            if (cycleCount == 0 && completedModifiers.isEmpty()) {
                ConsoleUtil.sendDebug("No completed cycles or modifiers for " + player.getName());
                return;
            }

            // Remove all previous reincarnation and modifier tags
            List<String> userTags = customTagsAPI.getUserTagIds(player);
            for (String tag : userTags) {
                if (tag.matches("^reincarnation_[0-9]+$") || tag.startsWith("reincarnation_")) {
                    customTagsAPI.removeUserTag(player, tag);
                }
            }

            // Add reincarnation level tag
            if (cycleCount > 0) {
                String reincarnationTagId = "reincarnation_" + cycleCount;
                if (customTagsAPI.giveUserTag(player, reincarnationTagId)) {
                    ConsoleUtil.sendDebug("Added reincarnation tag " + reincarnationTagId + " for " + player.getName());
                } else {
                    ConsoleUtil.sendError(
                            "Failed to add reincarnation tag " + reincarnationTagId + " for " + player.getName());
                }
            }

            // Add completed modifier tags
            for (String modifierId : completedModifiers) {
                String completedTag = "reincarnation_" + modifierId;
                if (customTagsAPI.giveUserTag(player, completedTag)) {
                    ConsoleUtil.sendDebug("Added modifier tag " + completedTag + " for " + player.getName());
                } else {
                    ConsoleUtil.sendError("Failed to add modifier tag " + completedTag + " for " + player.getName());
                }
            }

            // Set the reincarnation level tag as the selected tag if it exists
            if (cycleCount > 0) {
                String selectedTagId = "reincarnation_" + cycleCount;
                if (customTagsAPI.setUserSelectedTag(player, selectedTagId)) {
                    ConsoleUtil.sendDebug("Set " + selectedTagId + " as selected tag for " + player.getName());
                } else {
                    ConsoleUtil
                            .sendError("Failed to set " + selectedTagId + " as selected tag for " + player.getName());
                }
            }

            // Update LuckPerms permission for completion count
            updateCompletionPermission(player, cycleCount);

        } catch (SQLException e) {
            ConsoleUtil.sendError("Error updating tags for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void updateCompletionPermission(Player player, int cycleCount) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            ConsoleUtil.sendError("Unable to get LuckPerms user for " + player.getName());
            return;
        }

        // Remove all existing completion permissions
        Pattern completionPattern = Pattern.compile("reincarceration\\.completions\\.\\d+");
        user.data().clear(node -> node.getKey().startsWith("reincarceration.completions.") &&
                completionPattern.matcher(node.getKey()).matches());

        // Add the new completion permission
        String newPermission = "reincarceration.completions." + cycleCount;
        user.data().add(Node.builder(newPermission).build());

        // Save changes
        luckPerms.getUserManager().saveUser(user);

        ConsoleUtil.sendDebug("Updated completion permission for " + player.getName() + " to " + newPermission);
    }

    public boolean isAssociatedWithBaseGroup(UUID uuid) {
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms not found! Unable to check base group association.");
            return false;
        }

        String baseGroup = configManager.getBaseGroup();
        User user = luckPerms.getUserManager().getUser(uuid);

        if (user == null) {
            plugin.getLogger().warning("Unable to get LuckPerms user for " + uuid);
            return false;
        }

        // Check all inherited groups
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (isGroupOrParentBaseGroup(node.getGroupName(), baseGroup)) {
                return true;
            }
        }

        return false;
    }

    private boolean isGroupOrParentBaseGroup(String groupName, String baseGroup) {
        if (groupName.equals(baseGroup)) {
            return true;
        }

        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return false;
        }

        // Check parent groups
        for (InheritanceNode node : group.getNodes(NodeType.INHERITANCE)) {
            if (isGroupOrParentBaseGroup(node.getGroupName(), baseGroup)) {
                return true;
            }
        }

        return false;
    }

    public void updateCompletionGroups(Player player) throws SQLException {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            ConsoleUtil.sendError("Unable to get LuckPerms user for " + player.getName());
            return;
        }

        int cycleCount = dataManager.getPlayerCycleCount(player);
        List<String> completedModifiers = dataManager.getCompletedModifiers(player);

        // Remove all existing completion count groups
        user.data().clear(node -> node.getKey().startsWith("group.reincarnation_completion_"));

        // Add new completion count group if it exists
        String completionCountGroup = "reincarnation_completion_" + cycleCount;
        if (luckPerms.getGroupManager().getGroup(completionCountGroup) != null) {
            user.data().add(InheritanceNode.builder(completionCountGroup).build());
        } else {
            ConsoleUtil.sendDebug("Completion count group does not exist: " + completionCountGroup);
        }

        // Remove all existing modifier completion groups
        user.data().clear(node -> node.getKey().startsWith("group.reincarnation_modifier_"));

        // Add new modifier completion groups if they exist
        for (String modifierId : completedModifiers) {
            String modifierGroup = "reincarnation_modifier_" + modifierId;
            if (luckPerms.getGroupManager().getGroup(modifierGroup) != null) {
                user.data().add(InheritanceNode.builder(modifierGroup).build());
            } else {
                ConsoleUtil.sendDebug("Modifier group does not exist: " + modifierGroup);
            }
        }

        // Save changes
        luckPerms.getUserManager().saveUser(user);

        ConsoleUtil.sendDebug("Updated completion groups for " + player.getName() +
                ". Cycle count: " + cycleCount +
                ", Completed modifiers: " + String.join(", ", completedModifiers));
    }

    public void removeFromCompletionGroups(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            ConsoleUtil.sendError("Unable to get LuckPerms user for " + player.getName());
            return;
        }

        // Remove all completion count groups
        user.data().clear(node -> node.getKey().startsWith("group.reincarnation_completion_"));

        // Remove all modifier completion groups
        user.data().clear(node -> node.getKey().startsWith("group.reincarnation_modifier_"));

        // Save changes
        luckPerms.getUserManager().saveUser(user);

        ConsoleUtil.sendDebug("Removed all completion groups for " + player.getName());
    }

}