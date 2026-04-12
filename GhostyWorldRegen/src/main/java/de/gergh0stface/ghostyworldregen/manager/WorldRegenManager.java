package de.gergh0stface.ghostyworldregen.manager;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class WorldRegenManager {

    private final GhostyWorldRegen plugin;

    private final Set<String>          activeRegens    = new HashSet<>();
    private final Map<String, String>  pendingConfirms = new HashMap<>();
    private final Map<String, Integer> confirmTimers   = new HashMap<>();

    public WorldRegenManager(GhostyWorldRegen plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────

    public void requestRegeneration(CommandSender sender, String worldName) {
        LangManager   lang = plugin.getLangManager();
        ConfigManager cfg  = plugin.getConfigManager();

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            lang.send(sender, "world-not-found", "world", worldName);
            return;
        }
        if (activeRegens.contains(worldName)) {
            lang.send(sender, "regen-already-running", "world", worldName);
            return;
        }

        String senderName = sender.getName();
        cancelPendingConfirm(senderName);
        pendingConfirms.put(senderName, worldName);

        lang.send(sender, "regen-confirm-required",
                "world", worldName,
                "timeout", String.valueOf(cfg.getConfirmTimeout()));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (worldName.equals(pendingConfirms.get(senderName))) {
                pendingConfirms.remove(senderName);
                confirmTimers.remove(senderName);
                lang.send(sender, "regen-confirm-expired", "world", worldName);
            }
        }, cfg.getConfirmTimeout() * 20L).getTaskId();

        confirmTimers.put(senderName, taskId);
    }

    public void confirmRegeneration(CommandSender sender) {
        String senderName = sender.getName();
        LangManager lang  = plugin.getLangManager();

        if (!pendingConfirms.containsKey(senderName)) {
            lang.send(sender, "regen-no-pending");
            return;
        }
        String worldName = pendingConfirms.remove(senderName);
        cancelPendingConfirm(senderName);
        lang.send(sender, "regen-confirmed", "world", worldName);
        startRegeneration(sender, worldName);
    }

    private void cancelPendingConfirm(String senderName) {
        Integer taskId = confirmTimers.remove(senderName);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        pendingConfirms.remove(senderName);
    }

    // ── Regeneration Pipeline ─────────────────────────────────

    private void startRegeneration(CommandSender sender, String worldName) {
        int countdown = plugin.getConfigManager().getCountdown();
        if (countdown > 0) runCountdown(sender, worldName, countdown);
        else               executeRegen(sender, worldName);
    }

    private void runCountdown(CommandSender sender, String worldName, int seconds) {
        LangManager lang = plugin.getLangManager();
        new BukkitRunnable() {
            int remaining = seconds;
            @Override public void run() {
                if (remaining <= 0) { cancel(); executeRegen(sender, worldName); return; }
                String msg = lang.get("regen-countdown",
                        "world", worldName, "seconds", String.valueOf(remaining));
                if (plugin.getConfigManager().isBroadcastCountdown()) {
                    Bukkit.getServer().broadcast(
                            LegacyComponentSerializer.legacySection().deserialize(msg));
                } else {
                    sender.sendMessage(msg);
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void executeRegen(CommandSender sender, String worldName) {
        LangManager lang = plugin.getLangManager();
        activeRegens.add(worldName);
        long startTime = System.currentTimeMillis();

        new BukkitRunnable() {
            @Override public void run() {
                try {
                    lang.send(sender, "regen-start", "world", worldName);

                    // Capture world settings via Bukkit API — no MV dependency needed
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        lang.send(sender, "world-not-found", "world", worldName);
                        activeRegens.remove(worldName);
                        return;
                    }

                    World.Environment environment = world.getEnvironment();
                    long              seed        = world.getSeed();

                    // 1) Teleport players out
                    teleportPlayersOut(sender, worldName);

                    // 2) Auto-backup
                    if (plugin.getConfigManager().isAutoBackupEnabled()) {
                        File backup = plugin.getBackupManager().createBackup(worldName, sender);
                        if (backup == null) {
                            lang.send(sender, "regen-failed", "world", worldName);
                            activeRegens.remove(worldName);
                            plugin.getDiscordHook().sendRegenFailed(worldName, "Backup creation failed");
                            return;
                        }
                        plugin.getDiscordHook().sendBackupCreated(worldName, backup.getName());
                    } else {
                        lang.send(sender, "backup-not-needed", "world", worldName);
                    }

                    plugin.getDiscordHook().sendRegenStarted(worldName, sender.getName());

                    // 3) Unload via Bukkit
                    lang.send(sender, "regen-unloading", "world", worldName);
                    boolean unloaded = Bukkit.unloadWorld(worldName, false);
                    if (!unloaded) {
                        lang.send(sender, "regen-failed", "world", worldName);
                        activeRegens.remove(worldName);
                        plugin.getDiscordHook().sendRegenFailed(worldName, "Could not unload world");
                        return;
                    }

                    // 4) Delete folder
                    lang.send(sender, "regen-deleting", "world", worldName);
                    File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                    if (!plugin.getBackupManager().deleteWorldFolder(worldFolder)) {
                        lang.send(sender, "regen-failed", "world", worldName);
                        activeRegens.remove(worldName);
                        plugin.getDiscordHook().sendRegenFailed(worldName, "Could not delete world folder");
                        return;
                    }

                    // 5) Recreate via Bukkit WorldCreator — works regardless of MV version
                    lang.send(sender, "regen-recreating", "world", worldName);
                    WorldCreator creator = new WorldCreator(worldName)
                            .environment(environment)
                            .seed(seed);

                    World recreated = creator.createWorld();
                    if (recreated == null) {
                        lang.send(sender, "regen-failed", "world", worldName);
                        activeRegens.remove(worldName);
                        plugin.getDiscordHook().sendRegenFailed(worldName, "WorldCreator returned null");
                        return;
                    }

                    // 6) Record & notify
                    plugin.getDatabaseManager().setLastRegen(worldName, System.currentTimeMillis());
                    lang.send(sender, "regen-success", "world", worldName);
                    plugin.getLogger().info("World '" + worldName + "' successfully regenerated.");
                    plugin.getDiscordHook().sendRegenSuccess(worldName, sender.getName(),
                            System.currentTimeMillis() - startTime);

                } catch (Throwable e) {
                    plugin.getLogger().severe("Exception during regen of '" + worldName + "': " + e.getMessage());
                    e.printStackTrace();
                    lang.send(sender, "regen-failed", "world", worldName);
                    plugin.getDiscordHook().sendRegenFailed(worldName, e.getMessage());
                } finally {
                    activeRegens.remove(worldName);
                }
            }
        }.runTask(plugin);
    }

    // ── Player Teleport ───────────────────────────────────────

    private void teleportPlayersOut(CommandSender sender, String worldName) {
        if (!plugin.getConfigManager().isTeleportPlayersEnabled()) return;
        World regenWorld = Bukkit.getWorld(worldName);
        if (regenWorld == null) return;

        List<Player> toMove = new ArrayList<>(regenWorld.getPlayers());
        if (toMove.isEmpty()) return;

        World targetWorld = resolveTargetWorld(worldName);
        String targetName = targetWorld != null ? targetWorld.getName() : "spawn";

        plugin.getLangManager().send(sender, "regen-teleporting",
                "count", String.valueOf(toMove.size()), "world", worldName);

        for (Player player : toMove) {
            Location dest = targetWorld != null
                    ? targetWorld.getSpawnLocation()
                    : Bukkit.getServer().getWorlds().get(0).getSpawnLocation();
            player.teleport(dest);
            plugin.getLangManager().send(player, "regen-teleport-msg",
                    "world", worldName, "target", targetName);
        }
    }

    private World resolveTargetWorld(String excludeWorld) {
        String configured = plugin.getConfigManager().getTeleportWorld();
        if (configured != null && !configured.isEmpty()) {
            World w = Bukkit.getWorld(configured);
            if (w != null && !w.getName().equals(excludeWorld)) return w;
        }
        // Fall back to first world that isn't the one being regenerated
        for (World w : Bukkit.getServer().getWorlds()) {
            if (!w.getName().equals(excludeWorld)) return w;
        }
        return null;
    }

    // ── Getters ───────────────────────────────────────────────

    public boolean isRegenActive(String worldName)      { return activeRegens.contains(worldName); }
    public boolean hasPendingConfirm(String senderName) { return pendingConfirms.containsKey(senderName); }
}
