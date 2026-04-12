package de.gergh0stface.ghostyworldregen.gui;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import de.gergh0stface.ghostyworldregen.manager.DatabaseManager;
import de.gergh0stface.ghostyworldregen.manager.ScheduleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class WorldMenuGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 45;

    private final GhostyWorldRegen plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Set<Inventory> openInventories = Collections.newSetFromMap(new WeakHashMap<>());

    public WorldMenuGUI(GhostyWorldRegen plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Open ──────────────────────────────────────────────────

    public void open(Player player) {
        open(player, playerPages.getOrDefault(player.getUniqueId(), 0));
    }

    public void open(Player player, int page) {
        // Use Bukkit worlds — MV worlds are Bukkit worlds
        List<World> worlds = new ArrayList<>(Bukkit.getWorlds());

        int totalPages = Math.max(1, (int) Math.ceil(worlds.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(player.getUniqueId(), page);

        Component title = legacy("&8[&bGhosty&3WorldRegen&8] &7Worlds &8(" + (page + 1) + "/" + totalPages + ")");
        Inventory inv = Bukkit.createInventory(null, 54, title);
        openInventories.add(inv);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, worlds.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildWorldItem(worlds.get(i)));
        }

        if (page > 0)              inv.setItem(45, buildNavItem(Material.ARROW, "&aPrevious Page", "&7Page " + page));
        if (page < totalPages - 1) inv.setItem(53, buildNavItem(Material.ARROW, "&aNext Page", "&7Page " + (page + 2)));
        inv.setItem(49, buildNavItem(Material.NETHER_STAR,
                "&bTotal Worlds: &e" + worlds.size(),
                "&7Page &e" + (page + 1) + "&7/&e" + totalPages));
        inv.setItem(48, buildNavItem(Material.BARRIER, "&cClose", "&7Close this menu"));

        player.openInventory(inv);
    }

    // ── Item Builders ─────────────────────────────────────────

    private ItemStack buildWorldItem(World world) {
        String worldName    = world.getName();
        DatabaseManager db  = plugin.getDatabaseManager();
        ScheduleManager sm  = plugin.getScheduleManager();

        boolean regenActive = plugin.getWorldRegenManager().isRegenActive(worldName);
        boolean hasSchedule = sm.hasSchedule(worldName);
        long    lastRegen   = db.getLastRegen(worldName);
        int     backupCount = db.getBackupCount(worldName);
        int     playerCount = world.getPlayers().size();

        Material icon = regenActive ? Material.MAGMA_BLOCK : getEnvironmentMaterial(world.getEnvironment());
        ItemStack item = new ItemStack(icon);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String nameColor = regenActive ? "&c" : "&a";
        meta.displayName(legacy(nameColor + worldName));

        String sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(lastRegen));
        List<Component> lore = new ArrayList<>();
        lore.add(legacy("&8&m-----------------------------"));
        lore.add(legacy("&7Status:  " + (regenActive ? "&cRegenerating..." : "&aLoaded")));
        lore.add(legacy("&7Env:     &b" + world.getEnvironment().name()));
        lore.add(legacy("&7Players: &e" + playerCount));
        lore.add(legacy("&7Backups: &e" + backupCount));
        lore.add(legacy("&7Last Regen: &e" + (lastRegen == 0 ? "Never" : sdf)));
        if (hasSchedule) {
            long next = sm.getNextRegen(worldName);
            String nextStr = next == 0 ? "?" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(next));
            lore.add(legacy("&7Schedule: &d" + sm.getIntervalHours(worldName) + "h &8| Next: &d" + nextStr));
        }
        lore.add(legacy("&8&m-----------------------------"));
        lore.add(legacy("&e► &6Left-Click &7to regenerate"));
        lore.add(legacy("&e► &6Right-Click &7to backup"));
        lore.add(legacy("&e► &6Shift+Left &7for info"));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildNavItem(Material mat, String name, String loreText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(legacy(name));
        meta.lore(Collections.singletonList(legacy(loreText)));
        item.setItemMeta(meta);
        return item;
    }

    private Material getEnvironmentMaterial(World.Environment env) {
        return switch (env) {
            case NETHER  -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default      -> Material.GRASS_BLOCK;
        };
    }

    // ── Click Handler ─────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openInventories.contains(event.getInventory())) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 48) { player.closeInventory(); return; }
        if (slot == 45) { open(player, playerPages.getOrDefault(player.getUniqueId(), 0) - 1); return; }
        if (slot == 53) { open(player, playerPages.getOrDefault(player.getUniqueId(), 0) + 1); return; }
        if (slot == 49) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null || clickedMeta.displayName() == null) return;

        String worldName = LegacyComponentSerializer.legacySection()
                .serialize(clickedMeta.displayName())
                .replaceAll("§[0-9a-fk-or]", "")
                .trim();

        ClickType clickType = event.getClick();

        if (clickType == ClickType.LEFT) {
            player.closeInventory();
            if (!player.hasPermission("ghostyworldregen.regenerate")) {
                plugin.getLangManager().send(player, "no-permission"); return;
            }
            if (Bukkit.getWorld(worldName) == null) {
                plugin.getLangManager().send(player, "world-not-found", "world", worldName); return;
            }
            plugin.getWorldRegenManager().requestRegeneration(player, worldName);

        } else if (clickType == ClickType.RIGHT) {
            player.closeInventory();
            if (!player.hasPermission("ghostyworldregen.backup")) {
                plugin.getLangManager().send(player, "no-permission"); return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getBackupManager().createBackup(worldName, player));

        } else if (clickType == ClickType.SHIFT_LEFT) {
            player.closeInventory();
            sendWorldInfo(player, worldName);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openInventories.remove(event.getInventory());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isViewingGui(player)) playerPages.remove(player.getUniqueId());
        }, 2L);
    }

    // ── Info ──────────────────────────────────────────────────

    private void sendWorldInfo(Player player, String worldName) {
        DatabaseManager db   = plugin.getDatabaseManager();
        ScheduleManager sm   = plugin.getScheduleManager();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        long  lastRegen = db.getLastRegen(worldName);
        World world     = Bukkit.getWorld(worldName);

        player.sendMessage(legacy("&8&m============================================"));
        player.sendMessage(legacy("  &bGhostyWorldRegen &8| &3World Info: &e" + worldName));
        player.sendMessage(legacy("&8&m============================================"));
        player.sendMessage(legacy("  &7Status:    " + (world != null ? "&aLoaded" : "&7Unloaded")));
        if (world != null) {
            player.sendMessage(legacy("  &7Players:   &e" + world.getPlayers().size()));
            player.sendMessage(legacy("  &7Entities:  &e" + world.getEntities().size()));
            player.sendMessage(legacy("  &7Chunks:    &e" + world.getLoadedChunks().length + " loaded"));
            player.sendMessage(legacy("  &7Seed:      &b" + world.getSeed()));
            player.sendMessage(legacy("  &7Env:       &b" + world.getEnvironment().name()));
        }
        player.sendMessage(legacy("  &7Last Regen: &e" + (lastRegen == 0 ? "Never" : sdf.format(new Date(lastRegen)))));
        player.sendMessage(legacy("  &7Backups:   &e" + db.getBackupCount(worldName)));
        if (sm.hasSchedule(worldName)) {
            long next = sm.getNextRegen(worldName);
            player.sendMessage(legacy("  &7Schedule:  &d" + sm.getIntervalHours(worldName) + "h interval"));
            player.sendMessage(legacy("  &7Next Regen: &d" + (next == 0 ? "?" : sdf.format(new Date(next)))));
        } else {
            player.sendMessage(legacy("  &7Schedule:  &8None"));
        }
        player.sendMessage(legacy("&8&m============================================"));
    }

    // ── Helpers ───────────────────────────────────────────────

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private boolean isViewingGui(Player player) {
        if (player.getOpenInventory() == null) return false;
        return openInventories.contains(player.getOpenInventory().getTopInventory());
    }
}
