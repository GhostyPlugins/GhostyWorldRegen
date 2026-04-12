package de.gergh0stface.ghostyworldregen.manager;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import org.bukkit.command.CommandSender;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public class BackupManager {

    private final GhostyWorldRegen plugin;

    public BackupManager(GhostyWorldRegen plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a zip backup of the world folder.
     *
     * @param worldName  Name of the world
     * @param sender     CommandSender to notify (may be null for async silent)
     * @return Path to the created backup file, or null on failure
     */
    public File createBackup(String worldName, CommandSender sender) {
        LangManager lang = plugin.getLangManager();
        ConfigManager cfg = plugin.getConfigManager();

        File worldFolder = new File(plugin.getServer().getWorldContainer(), worldName);
        if (!worldFolder.exists() || !worldFolder.isDirectory()) {
            if (sender != null) lang.send(sender, "backup-failed", "world", worldName);
            plugin.getLogger().warning("Backup failed: world folder not found for '" + worldName + "'");
            return null;
        }

        if (sender != null) lang.send(sender, "backup-start", "world", worldName);

        // Prepare backup folder
        File backupDir = new File(cfg.getBackupFolder());
        File worldBackupDir = new File(backupDir, worldName);
        if (!worldBackupDir.exists()) worldBackupDir.mkdirs();

        // Build zip filename
        String timestamp = new SimpleDateFormat(cfg.getTimestampFormat()).format(new Date());
        File zipFile = new File(worldBackupDir, worldName + "_" + timestamp + ".zip");

        try {
            zipFolder(worldFolder.toPath(), zipFile.toPath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create backup for world '" + worldName + "': " + e.getMessage());
            if (sender != null) lang.send(sender, "backup-failed", "world", worldName);
            return null;
        }

        // Register backup
        plugin.getDatabaseManager().addBackupEntry(worldName, zipFile.getAbsolutePath());

        // Cleanup old backups if limit set
        int max = cfg.getMaxBackupsPerWorld();
        if (max > 0) {
            enforceBackupLimit(worldName, max, sender);
        }

        if (sender != null) lang.send(sender, "backup-success", "world", worldName, "file", zipFile.getName());
        plugin.getLogger().info("Backup created: " + zipFile.getAbsolutePath());
        return zipFile;
    }

    private void enforceBackupLimit(String worldName, int max, CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        while (db.getBackupCount(worldName) > max) {
            List<String> files = db.getBackupFiles(worldName);
            if (files.isEmpty()) break;
            File oldest = new File(files.get(0));
            if (oldest.exists()) oldest.delete();
            db.removeOldestBackup(worldName);
            if (sender != null) {
                plugin.getLangManager().send(sender, "backup-max-reached",
                        "world", worldName, "max", String.valueOf(max));
            }
        }
    }

    private void zipFolder(Path sourceFolder, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipPath.toFile())))) {
            Files.walk(sourceFolder).forEach(path -> {
                if (Files.isDirectory(path)) return;
                ZipEntry entry = new ZipEntry(sourceFolder.relativize(path).toString().replace('\\', '/'));
                try {
                    zos.putNextEntry(entry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Delete the physical world folder recursively.
     */
    public boolean deleteWorldFolder(File worldFolder) {
        if (!worldFolder.exists()) return true;
        try {
            Files.walk(worldFolder.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
            return !worldFolder.exists();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to delete world folder: " + e.getMessage());
            return false;
        }
    }
}
