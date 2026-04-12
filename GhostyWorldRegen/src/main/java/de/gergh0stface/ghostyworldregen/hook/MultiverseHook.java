package de.gergh0stface.ghostyworldregen.hook;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * Optional Multiverse-Core integration using 100% reflection.
 * Works with ALL versions of Multiverse (legacy com.onarandombox AND new org.mvplugins).
 * If reflection fails for any reason, falls back to Bukkit.getWorlds() silently.
 */
public class MultiverseHook {

    private MultiverseHook() {}

    /**
     * Returns true if any known Multiverse plugin variant is enabled.
     */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core");
    }

    /**
     * Returns a list of world names managed by Multiverse via reflection.
     * Falls back to all Bukkit worlds if MV is unavailable or reflection fails.
     */
    public static List<String> getWorldNames() {
        if (isAvailable()) {
            try {
                return fetchWorldNamesViaReflection();
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[GhostyWorldRegen] MV reflection failed, using Bukkit worlds: " + t.getMessage());
            }
        }
        List<String> names = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) names.add(w.getName());
        return names;
    }

    private static List<String> fetchWorldNamesViaReflection() throws Exception {
        Plugin mvPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mvPlugin == null) throw new IllegalStateException("Multiverse-Core plugin instance is null");

        // Try to get MVWorldManager — works on both legacy and new MV via duck-typing
        Object worldManager = null;
        for (String methodName : new String[]{"getMVWorldManager", "getWorldManager"}) {
            try {
                Method m = mvPlugin.getClass().getMethod(methodName);
                worldManager = m.invoke(mvPlugin);
                if (worldManager != null) break;
            } catch (NoSuchMethodException ignored) {}
        }
        if (worldManager == null) throw new IllegalStateException("Could not obtain MV world manager");

        // Get the collection of MV worlds
        Collection<?> mvWorlds = null;
        for (String methodName : new String[]{"getMVWorlds", "getLoadedWorlds"}) {
            try {
                Method m = worldManager.getClass().getMethod(methodName);
                Object result = m.invoke(worldManager);
                if (result instanceof Collection) { mvWorlds = (Collection<?>) result; break; }
            } catch (NoSuchMethodException ignored) {}
        }
        if (mvWorlds == null) throw new IllegalStateException("Could not obtain MV world list");

        // Extract world names from each MV world object
        List<String> names = new ArrayList<>();
        for (Object mvWorld : mvWorlds) {
            String name = null;
            for (String methodName : new String[]{"getName", "getAlias"}) {
                try {
                    Method m = mvWorld.getClass().getMethod(methodName);
                    Object val = m.invoke(mvWorld);
                    if (val instanceof String s && !s.isEmpty()) { name = s; break; }
                } catch (NoSuchMethodException ignored) {}
            }
            if (name != null) names.add(name);
        }
        return names;
    }
}
