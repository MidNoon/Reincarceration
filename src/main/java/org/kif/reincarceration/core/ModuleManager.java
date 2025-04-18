package org.kif.reincarceration.core;

import lombok.Getter;
import org.kif.reincarceration.Reincarceration;
import org.kif.reincarceration.config.ConfigManager;
import org.kif.reincarceration.util.ConsoleUtil;

import java.sql.SQLException;
import java.util.*;

public class ModuleManager {
    private final Reincarceration plugin;
    private final Map<Class<? extends org.kif.reincarceration.core.Module>, ModuleInfo> modules;

    public ModuleManager(Reincarceration plugin) {
        this.plugin = plugin;
        this.modules = new HashMap<>();
    }

    @SafeVarargs
    public final void registerModule(org.kif.reincarceration.core.Module module, Class<? extends org.kif.reincarceration.core.Module>... dependencies) {
        ModuleInfo moduleInfo = new ModuleInfo(module, dependencies);
        modules.put(module.getClass(), moduleInfo);
    }

    public void enableModule(Class<? extends org.kif.reincarceration.core.Module> moduleClass) throws SQLException {
        ModuleInfo moduleInfo = modules.get(moduleClass);
        if (moduleInfo != null && moduleInfo.getState() == ModuleState.REGISTERED) {
            for (Class<? extends org.kif.reincarceration.core.Module> dependency : moduleInfo.getDependencies()) {
                enableModule(dependency);
            }
            try {
                moduleInfo.getModule().onEnable();
                moduleInfo.setState(ModuleState.ENABLED);
                ConsoleUtil.sendSuccess("Module enabled: " + moduleClass.getSimpleName());
            } catch (SQLException e) {
                plugin.getLogger().severe("Error enabling module " + moduleClass.getSimpleName() + ": " + e.getMessage());
                throw e;
            }
        }
    }

    public void disableAllModules() {
        List<Class<? extends org.kif.reincarceration.core.Module>> sortedModules = topologicalSort();
        Collections.reverse(sortedModules);
        for (Class<? extends org.kif.reincarceration.core.Module> moduleClass : sortedModules) {
            disableModule(moduleClass);
        }
    }

    public void disableModule(Class<? extends org.kif.reincarceration.core.Module> moduleClass) {
        ModuleInfo moduleInfo = modules.get(moduleClass);
        if (moduleInfo != null && moduleInfo.getState() == ModuleState.ENABLED) {
            moduleInfo.getModule().onDisable();
            moduleInfo.setState(ModuleState.DISABLED);
            ConsoleUtil.sendSuccess("Module disabled: " + moduleClass.getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends org.kif.reincarceration.core.Module> T getModule(Class<T> moduleClass) {
        ModuleInfo moduleInfo = modules.get(moduleClass);
        return moduleInfo != null ? (T) moduleInfo.getModule() : null;
    }

    public ConfigManager getConfigManager() {
        CoreModule coreModule = getModule(CoreModule.class);
        return coreModule != null ? coreModule.getConfigManager() : null;
    }

    public Reincarceration getPlugin() {
        return plugin;
    }

    private List<Class<? extends org.kif.reincarceration.core.Module>> topologicalSort() {
        Map<Class<? extends org.kif.reincarceration.core.Module>, Boolean> visited = new HashMap<>();
        List<Class<? extends org.kif.reincarceration.core.Module>> sortedModules = new ArrayList<>();

        for (Class<? extends org.kif.reincarceration.core.Module> moduleClass : modules.keySet()) {
            if (!visited.getOrDefault(moduleClass, false)) {
                topologicalSortUtil(moduleClass, visited, sortedModules);
            }
        }

        return sortedModules;
    }

    private void topologicalSortUtil(Class<? extends org.kif.reincarceration.core.Module> moduleClass, Map<Class<? extends org.kif.reincarceration.core.Module>, Boolean> visited, List<Class<? extends org.kif.reincarceration.core.Module>> sortedModules) {
        visited.put(moduleClass, true);
        ModuleInfo moduleInfo = modules.get(moduleClass);
        if (moduleInfo != null) {
            for (Class<? extends org.kif.reincarceration.core.Module> dependency : moduleInfo.getDependencies()) {
                if (!visited.getOrDefault(dependency, false)) {
                    topologicalSortUtil(dependency, visited, sortedModules);
                }
            }
        }
        sortedModules.add(moduleClass);
    }

    @Getter
    private static class ModuleInfo {
        private final org.kif.reincarceration.core.Module module;
        private final Class<? extends org.kif.reincarceration.core.Module>[] dependencies;
        private ModuleState state;

        public ModuleInfo(org.kif.reincarceration.core.Module module, Class<? extends org.kif.reincarceration.core.Module>[] dependencies) {
            this.module = module;
            this.dependencies = dependencies;
            this.state = ModuleState.REGISTERED;
        }

        public void setState(ModuleState state) {
            this.state = state;
        }
    }

    private enum ModuleState {
        REGISTERED, ENABLED, DISABLED
    }
}