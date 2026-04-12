package de.gergh0stface.ghostyworldregen.manager;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages automatic, time-based world regeneration schedules.
 *
 * Schedules are stored in schedules.yml:
 *
 *   schedules:
 *     world_nether:
 *       enabled: true
 *       interval-hours: 24
 *       next-regen: 1713000000000   # unix millis
 */
public class ScheduleManager {

    private final GhostyWorldRegen plugin;
    private File schedulesFile;
    private FileConfiguration schedulesConfig;

    // worldName -> BukkitTask
    private final Map<String, BukkitTask> activeTasks = new HashMap<>();

    public ScheduleManager(GhostyWorldRegen plugin) {
        this.plugin = plugin;
        loadFile();
        startAllSchedules();
    }

    // ── File I/O ──────────────────────────────────────────────

    private void loadFile() {
        schedulesFile = new File(plugin.getDataFolder(), "schedules.yml");
        if (!schedulesFile.exists()) {
            try {
                schedulesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create schedules.yml", e);
            }
        }
        schedulesConfig = YamlConfiguration.loadConfiguration(schedulesFile);
    }

    private void saveFile() {
        try {
            schedulesConfig.save(schedulesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save schedules.yml", e);
        }
    }

    public void reload() {
        cancelAllTasks();
        loadFile();
        startAllSchedules();
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Set a schedule for a world. Pass intervalHours = 0 to remove.
     */
    public void setSchedule(String world, int intervalHours) {
        if (intervalHours <= 0) {
            removeSchedule(world);
            return;
        }

        long nextRegen = System.currentTimeMillis() + (intervalHours * 3600_000L);
        schedulesConfig.set("schedules." + world + ".enabled", true);
        schedulesConfig.set("schedules." + world + ".interval-hours", intervalHours);
        schedulesConfig.set("schedules." + world + ".next-regen", nextRegen);
        saveFile();

        // Cancel any existing task for this world, then restart
        cancelTask(world);
        scheduleWorld(world, intervalHours, nextRegen);
        plugin.getLogger().info("[Schedule] World '" + world + "' scheduled every " + intervalHours + "h. Next: " + new Date(nextRegen));
    }

    public void removeSchedule(String world) {
        schedulesConfig.set("schedules." + world, null);
        saveFile();
        cancelTask(world);
        plugin.getLogger().info("[Schedule] Removed schedule for world '" + world + "'.");
    }

    public boolean hasSchedule(String world) {
        return schedulesConfig.isConfigurationSection("schedules." + world)
                && schedulesConfig.getBoolean("schedules." + world + ".enabled", false);
    }

    public int getIntervalHours(String world) {
        return schedulesConfig.getInt("schedules." + world + ".interval-hours", 0);
    }

    public long getNextRegen(String world) {
        return schedulesConfig.getLong("schedules." + world + ".next-regen", 0L);
    }

    public List<String> getAllScheduledWorlds() {
        ConfigurationSection section = schedulesConfig.getConfigurationSection("schedules");
        if (section == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (section.getBoolean(key + ".enabled", false)) {
                result.add(key);
            }
        }
        return result;
    }

    // ── Scheduling Logic ──────────────────────────────────────

    private void startAllSchedules() {
        ConfigurationSection section = schedulesConfig.getConfigurationSection("schedules");
        if (section == null) return;

        for (String world : section.getKeys(false)) {
            boolean enabled = section.getBoolean(world + ".enabled", false);
            if (!enabled) continue;

            int hours = section.getInt(world + ".interval-hours", 24);
            long nextRegen = section.getLong(world + ".next-regen", 0L);

            scheduleWorld(world, hours, nextRegen);
        }
        plugin.getLogger().info("[Schedule] Loaded " + activeTasks.size() + " world schedule(s).");
    }

    private void scheduleWorld(String world, int intervalHours, long nextRegen) {
        long now = System.currentTimeMillis();
        long delayMillis = nextRegen - now;

        // If overdue, run after a small grace period (30s)
        if (delayMillis < 0) {
            plugin.getLogger().info("[Schedule] World '" + world + "' regen was overdue. Scheduling in 30s.");
            delayMillis = 30_000L;
        }

        long delayTicks = delayMillis / 50L;   // ms → ticks (20tps = 50ms/tick)
        long periodTicks = intervalHours * 72_000L; // hours → ticks

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getWorld(world) == null) {
                plugin.getLogger().warning("[Schedule] Skipping regen of '" + world + "': world not loaded.");
                return;
            }
            plugin.getLogger().info("[Schedule] Auto-regen triggered for world '" + world + "'.");

            // Trigger regen via console sender
            plugin.getWorldRegenManager().requestRegeneration(
                    Bukkit.getConsoleSender(), world
            );
            // Auto-confirm immediately (scheduled runs don't need manual confirmation)
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getWorldRegenManager().confirmRegeneration(Bukkit.getConsoleSender()), 5L);

            // Update next-regen timestamp
            long next = System.currentTimeMillis() + (intervalHours * 3600_000L);
            schedulesConfig.set("schedules." + world + ".next-regen", next);
            saveFile();

        }, delayTicks, periodTicks);

        activeTasks.put(world, task);
    }

    private void cancelTask(String world) {
        BukkitTask task = activeTasks.remove(world);
        if (task != null) task.cancel();
    }

    private void cancelAllTasks() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }

    public void shutdown() {
        cancelAllTasks();
    }
}
