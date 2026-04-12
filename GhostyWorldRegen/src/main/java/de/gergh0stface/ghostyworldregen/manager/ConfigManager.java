package de.gergh0stface.ghostyworldregen.manager;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import org.bukkit.configuration.file.FileConfiguration;


public class ConfigManager {

    private final GhostyWorldRegen plugin;
    private FileConfiguration config;

    public ConfigManager(GhostyWorldRegen plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        load();
    }

    // ── Storage ───────────────────────────────────────────────
    public String getStorageType() {
        return config.getString("storage.type", "yml").toLowerCase();
    }

    public boolean isMySQL() {
        return getStorageType().equals("mysql");
    }

    public String getMySQLHost()     { return config.getString("storage.mysql.host", "localhost"); }
    public int    getMySQLPort()     { return config.getInt("storage.mysql.port", 3306); }
    public String getMySQLDatabase() { return config.getString("storage.mysql.database", "ghostyworldregen"); }
    public String getMySQLUsername() { return config.getString("storage.mysql.username", "root"); }
    public String getMySQLPassword() { return config.getString("storage.mysql.password", ""); }
    public boolean getMySQLSSL()     { return config.getBoolean("storage.mysql.useSSL", false); }
    public int    getPoolSize()      { return config.getInt("storage.mysql.connection-pool-size", 5); }

    // ── Backup ────────────────────────────────────────────────
    public String getBackupFolder()       { return config.getString("backup.folder", "plugins/GhostyWorldRegen/backups"); }
    public int    getMaxBackupsPerWorld() { return config.getInt("backup.max-per-world", 5); }
    public boolean isAutoBackupEnabled()  { return config.getBoolean("backup.auto-backup-before-regen", true); }
    public String getTimestampFormat()    { return config.getString("backup.timestamp-format", "yyyy-MM-dd_HH-mm-ss"); }

    // ── Regen ─────────────────────────────────────────────────
    public boolean isTeleportPlayersEnabled() { return config.getBoolean("regen.teleport-players", true); }
    public String  getTeleportWorld()          { return config.getString("regen.teleport-world", ""); }
    public int     getCountdown()              { return config.getInt("regen.countdown", 10); }
    public boolean isBroadcastCountdown()      { return config.getBoolean("regen.broadcast-countdown", true); }
    public int     getConfirmTimeout()         { return config.getInt("regen.confirm-timeout", 30); }

    // ── Language ──────────────────────────────────────────────
    public String getLanguage() { return config.getString("language", "en"); }

    // ── Prefix ────────────────────────────────────────────────
    public String getPrefix() { return config.getString("prefix", "&8[&bGhosty&3WorldRegen&8]&r"); }

    public FileConfiguration getRaw() { return config; }
}
