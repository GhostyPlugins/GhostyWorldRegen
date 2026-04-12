package de.gergh0stface.ghostyworldregen.manager;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DatabaseManager {

    private final GhostyWorldRegen plugin;
    private FileConfiguration dataYml;
    private File dataFile;
    private Connection mysqlConnection;

    public DatabaseManager(GhostyWorldRegen plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (plugin.getConfigManager().isMySQL()) {
            initMySQL();
        } else {
            initYml();
        }
    }

    // ── YML ───────────────────────────────────────────────────
    private void initYml() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not create data.yml", e); }
        }
        dataYml = YamlConfiguration.loadConfiguration(dataFile);
        plugin.getLogger().info("Using YML storage (data.yml).");
    }

    private void saveYml() {
        if (dataYml == null || dataFile == null) return;
        try { dataYml.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e); }
    }

    // ── MySQL ─────────────────────────────────────────────────
    private void initMySQL() {
        ConfigManager cfg = plugin.getConfigManager();
        String url = "jdbc:mysql://" + cfg.getMySQLHost() + ":" + cfg.getMySQLPort()
                + "/" + cfg.getMySQLDatabase()
                + "?useSSL=" + cfg.getMySQLSSL()
                + "&autoReconnect=true"
                + "&characterEncoding=UTF-8";
        try {
            mysqlConnection = DriverManager.getConnection(url, cfg.getMySQLUsername(), cfg.getMySQLPassword());
            createTables();
            plugin.getLogger().info("Connected to MySQL database successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to MySQL! Falling back to YML.", e);
            initYml();
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = mysqlConnection.createStatement()) {
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS gwr_worlds (" +
                "  world_name VARCHAR(255) NOT NULL PRIMARY KEY," +
                "  last_regen BIGINT DEFAULT 0," +
                "  regen_count INT DEFAULT 0" +
                ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS gwr_backups (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  world_name VARCHAR(255) NOT NULL," +
                "  backup_file VARCHAR(512) NOT NULL," +
                "  created_at BIGINT NOT NULL" +
                ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
            );
        }
    }

    public void close() {
        if (mysqlConnection != null) {
            try { mysqlConnection.close(); }
            catch (SQLException ignored) {}
            mysqlConnection = null;
        }
    }

    // ── World Data ────────────────────────────────────────────

    public long getLastRegen(String world) {
        if (isUsingMySQL()) {
            try (PreparedStatement ps = mysqlConnection.prepareStatement(
                    "SELECT last_regen FROM gwr_worlds WHERE world_name = ?")) {
                ps.setString(1, world);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getLong("last_regen");
            } catch (SQLException e) { logError(e); }
            return 0L;
        } else {
            return dataYml.getLong("worlds." + world + ".last-regen", 0L);
        }
    }

    public void setLastRegen(String world, long timestamp) {
        if (isUsingMySQL()) {
            try (PreparedStatement ps = mysqlConnection.prepareStatement(
                    "INSERT INTO gwr_worlds (world_name, last_regen, regen_count) VALUES (?, ?, 1) " +
                    "ON DUPLICATE KEY UPDATE last_regen = ?, regen_count = regen_count + 1")) {
                ps.setString(1, world);
                ps.setLong(2, timestamp);
                ps.setLong(3, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) { logError(e); }
        } else {
            dataYml.set("worlds." + world + ".last-regen", timestamp);
            int count = dataYml.getInt("worlds." + world + ".regen-count", 0) + 1;
            dataYml.set("worlds." + world + ".regen-count", count);
            saveYml();
        }
    }

    // ── Backup Data ───────────────────────────────────────────

    public void addBackupEntry(String world, String filePath) {
        long now = System.currentTimeMillis();
        if (isUsingMySQL()) {
            try (PreparedStatement ps = mysqlConnection.prepareStatement(
                    "INSERT INTO gwr_backups (world_name, backup_file, created_at) VALUES (?, ?, ?)")) {
                ps.setString(1, world);
                ps.setString(2, filePath);
                ps.setLong(3, now);
                ps.executeUpdate();
            } catch (SQLException e) { logError(e); }
        } else {
            List<String> backups = new ArrayList<>(dataYml.getStringList("backups." + world));
            backups.add(now + "|" + filePath);
            dataYml.set("backups." + world, backups);
            saveYml();
        }
    }

    public List<String> getBackupFiles(String world) {
        List<String> result = new ArrayList<>();
        if (isUsingMySQL()) {
            try (PreparedStatement ps = mysqlConnection.prepareStatement(
                    "SELECT backup_file FROM gwr_backups WHERE world_name = ? ORDER BY created_at ASC")) {
                ps.setString(1, world);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(rs.getString("backup_file"));
            } catch (SQLException e) { logError(e); }
        } else {
            for (String entry : dataYml.getStringList("backups." + world)) {
                String[] parts = entry.split("\\|", 2);
                if (parts.length == 2) result.add(parts[1]);
            }
        }
        return result;
    }

    public void removeOldestBackup(String world) {
        if (isUsingMySQL()) {
            try (PreparedStatement ps = mysqlConnection.prepareStatement(
                    "DELETE FROM gwr_backups WHERE id = (" +
                    "  SELECT id FROM gwr_backups WHERE world_name = ? ORDER BY created_at ASC LIMIT 1)")) {
                ps.setString(1, world);
                ps.executeUpdate();
            } catch (SQLException e) { logError(e); }
        } else {
            List<String> backups = new ArrayList<>(dataYml.getStringList("backups." + world));
            if (!backups.isEmpty()) {
                backups.remove(0);
                dataYml.set("backups." + world, backups);
                saveYml();
            }
        }
    }

    public int getBackupCount(String world) {
        return getBackupFiles(world).size();
    }

    public List<String> getAllWorldNames() {
        List<String> worlds = new ArrayList<>();
        if (isUsingMySQL()) {
            try (Statement stmt = mysqlConnection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT world_name FROM gwr_worlds")) {
                while (rs.next()) worlds.add(rs.getString("world_name"));
            } catch (SQLException e) { logError(e); }
        } else {
            if (dataYml.isConfigurationSection("worlds")) {
                worlds.addAll(dataYml.getConfigurationSection("worlds").getKeys(false));
            }
        }
        return worlds;
    }

    // ── Helpers ───────────────────────────────────────────────
    private boolean isUsingMySQL() {
        return mysqlConnection != null;
    }

    private void logError(SQLException e) {
        plugin.getLogger().log(Level.SEVERE, "MySQL error:", e);
    }
}
