package de.gergh0stface.ghostyworldregen.command;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import de.gergh0stface.ghostyworldregen.hook.MultiverseHook;
import de.gergh0stface.ghostyworldregen.manager.*;
import de.gergh0stface.ghostyworldregen.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class RegenCommand implements CommandExecutor, TabCompleter {

    private final GhostyWorldRegen plugin;

    public RegenCommand(GhostyWorldRegen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LangManager lang = plugin.getLangManager();
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {

            case "regenerate", "regen", "reg" -> {
                if (!sender.hasPermission("ghostyworldregen.regenerate")) { lang.send(sender, "no-permission"); return true; }
                if (args.length < 2) { sender.sendMessage(ColorUtil.colorize("&cUsage: /ghostyregen regenerate <world>")); return true; }
                String w = args[1];
                if (Bukkit.getWorld(w) == null) { lang.send(sender, "world-not-found", "world", w); return true; }
                plugin.getWorldRegenManager().requestRegeneration(sender, w);
            }

            case "confirm" -> {
                if (!sender.hasPermission("ghostyworldregen.regenerate")) { lang.send(sender, "no-permission"); return true; }
                plugin.getWorldRegenManager().confirmRegeneration(sender);
            }

            case "backup" -> {
                if (!sender.hasPermission("ghostyworldregen.backup")) { lang.send(sender, "no-permission"); return true; }
                if (args.length < 2) { sender.sendMessage(ColorUtil.colorize("&cUsage: /ghostyregen backup <world>")); return true; }
                String w = args[1];
                if (Bukkit.getWorld(w) == null) { lang.send(sender, "world-not-found", "world", w); return true; }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                        plugin.getBackupManager().createBackup(w, sender));
            }

            case "list" -> {
                if (!sender.hasPermission("ghostyworldregen.list")) { lang.send(sender, "no-permission"); return true; }
                sendList(sender);
            }

            case "info" -> {
                if (!sender.hasPermission("ghostyworldregen.info")) { lang.send(sender, "no-permission"); return true; }
                if (args.length < 2) { sender.sendMessage(ColorUtil.colorize("&cUsage: /ghostyregen info <world>")); return true; }
                sendInfo(sender, args[1]);
            }

            case "gui", "menu" -> {
                if (!(sender instanceof Player player)) { lang.send(sender, "player-only"); return true; }
                if (!player.hasPermission("ghostyworldregen.gui")) { lang.send(sender, "no-permission"); return true; }
                plugin.getWorldMenuGUI().open(player);
            }

            case "schedule" -> {
                if (!sender.hasPermission("ghostyworldregen.schedule")) { lang.send(sender, "no-permission"); return true; }
                handleSchedule(sender, args);
            }

            case "reload" -> {
                if (!sender.hasPermission("ghostyworldregen.reload")) { lang.send(sender, "no-permission"); return true; }
                plugin.reload();
                lang.send(sender, "reload-success");
            }

            case "help" -> sendHelp(sender);
            default -> lang.send(sender, "unknown-command");
        }
        return true;
    }

    // ── Schedule ──────────────────────────────────────────────

    private void handleSchedule(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.colorize("&cUsage: /ghostyregen schedule <world> <hours|remove>"));
            sender.sendMessage(ColorUtil.colorize("&7Example: &e/ghostyregen schedule my_world 24"));
            return;
        }
        String worldName   = args[1];
        String intervalArg = args[2];

        if (Bukkit.getWorld(worldName) == null) {
            plugin.getLangManager().send(sender, "world-not-found", "world", worldName);
            return;
        }

        ScheduleManager sm = plugin.getScheduleManager();
        if (intervalArg.equalsIgnoreCase("remove") || intervalArg.equalsIgnoreCase("off")) {
            sm.removeSchedule(worldName);
            plugin.getLangManager().send(sender, "schedule-removed", "world", worldName);
            return;
        }
        try {
            int hours = Integer.parseInt(intervalArg);
            if (hours < 1) { sender.sendMessage(ColorUtil.colorize("&cInterval must be at least 1 hour.")); return; }
            sm.setSchedule(worldName, hours);
            plugin.getLangManager().send(sender, "schedule-set", "world", worldName, "hours", String.valueOf(hours));
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.colorize("&cInvalid interval. Use a number of hours or 'remove'."));
        }
    }

    // ── List ──────────────────────────────────────────────────

    private void sendList(CommandSender sender) {
        LangManager lang   = plugin.getLangManager();
        DatabaseManager db = plugin.getDatabaseManager();
        SimpleDateFormat sdf = new SimpleDateFormat(plugin.getConfigManager().getTimestampFormat());

        sender.sendMessage(lang.getNoPrefix("world-list-header"));
        List<String> tracked = db.getAllWorldNames();
        if (tracked.isEmpty()) { sender.sendMessage(lang.getNoPrefix("world-list-empty")); return; }
        for (String w : tracked) {
            long lastRegen = db.getLastRegen(w);
            String lastStr = lastRegen == 0 ? lang.getNoPrefix("world-list-never") : sdf.format(new Date(lastRegen));
            sender.sendMessage(lang.getNoPrefix("world-list-entry",
                    "world", w, "last_regen", lastStr,
                    "backups", String.valueOf(db.getBackupCount(w))));
        }
    }

    // ── Info ──────────────────────────────────────────────────

    private void sendInfo(CommandSender sender, String worldName) {
        DatabaseManager db   = plugin.getDatabaseManager();
        ScheduleManager sm   = plugin.getScheduleManager();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        long  lastRegen = db.getLastRegen(worldName);
        World world     = Bukkit.getWorld(worldName);

        sender.sendMessage(ColorUtil.colorize("&8&m============================================"));
        sender.sendMessage(ColorUtil.colorize("  &bGhostyWorldRegen &8| &3World Info: &e" + worldName));
        sender.sendMessage(ColorUtil.colorize("&8&m============================================"));
        sender.sendMessage(ColorUtil.colorize("  &7Status:     " + (world != null ? "&aLoaded" : "&7Unloaded")));
        if (world != null) {
            sender.sendMessage(ColorUtil.colorize("  &7Players:    &e" + world.getPlayers().size()));
            sender.sendMessage(ColorUtil.colorize("  &7Entities:   &e" + world.getEntities().size()));
            sender.sendMessage(ColorUtil.colorize("  &7Chunks:     &e" + world.getLoadedChunks().length + " loaded"));
            sender.sendMessage(ColorUtil.colorize("  &7Seed:       &b" + world.getSeed()));
            sender.sendMessage(ColorUtil.colorize("  &7Env:        &b" + world.getEnvironment().name()));
        }
        sender.sendMessage(ColorUtil.colorize("  &7Last Regen: &e" + (lastRegen == 0 ? "Never" : sdf.format(new Date(lastRegen)))));
        sender.sendMessage(ColorUtil.colorize("  &7Backups:    &e" + db.getBackupCount(worldName)));
        if (sm.hasSchedule(worldName)) {
            long next = sm.getNextRegen(worldName);
            sender.sendMessage(ColorUtil.colorize("  &7Schedule:   &d" + sm.getIntervalHours(worldName) + "h interval"));
            sender.sendMessage(ColorUtil.colorize("  &7Next Regen: &d" + (next == 0 ? "?" : sdf.format(new Date(next)))));
        } else {
            sender.sendMessage(ColorUtil.colorize("  &7Schedule:   &8None set"));
        }
        sender.sendMessage(ColorUtil.colorize("&8&m============================================"));
    }

    // ── Help ──────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        LangManager lang = plugin.getLangManager();
        sender.sendMessage(lang.getNoPrefix("help-header"));
        sender.sendMessage(lang.getNoPrefix("help-regen"));
        sender.sendMessage(lang.getNoPrefix("help-backup"));
        sender.sendMessage(lang.getNoPrefix("help-list"));
        sender.sendMessage(lang.getNoPrefix("help-info"));
        sender.sendMessage(lang.getNoPrefix("help-schedule"));
        sender.sendMessage(lang.getNoPrefix("help-gui"));
        sender.sendMessage(lang.getNoPrefix("help-reload"));
        sender.sendMessage(lang.getNoPrefix("help-footer"));
    }

    // ── Tab Completion ────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("regenerate", "backup", "confirm", "schedule", "info", "list", "gui", "reload", "help")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("regenerate", "regen", "reg", "backup", "info", "schedule").contains(sub)) {
                // Use MV world names if available, otherwise all Bukkit worlds — no class cast risk
                return getWorldNames().stream()
                        .filter(w -> w.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("schedule")) {
            return Arrays.asList("1", "6", "12", "24", "48", "72", "168", "remove")
                    .stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /** Safe world name list — MV via reflection, falls back to Bukkit worlds. */
    private List<String> getWorldNames() {
        List<String> names = MultiverseHook.getWorldNames();
        if (names.isEmpty()) {
            names = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
        }
        return names;
    }
}
