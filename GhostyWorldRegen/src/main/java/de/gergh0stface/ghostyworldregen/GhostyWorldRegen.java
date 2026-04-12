package de.gergh0stface.ghostyworldregen;

import de.gergh0stface.ghostyworldregen.command.RegenCommand;
import de.gergh0stface.ghostyworldregen.gui.WorldMenuGUI;
import de.gergh0stface.ghostyworldregen.hook.DiscordHook;
import de.gergh0stface.ghostyworldregen.manager.*;
import org.bukkit.plugin.java.JavaPlugin;

public class GhostyWorldRegen extends JavaPlugin {

    private static GhostyWorldRegen instance;

    private ConfigManager configManager;
    private LangManager langManager;
    private DatabaseManager databaseManager;
    private BackupManager backupManager;
    private WorldRegenManager worldRegenManager;
    private ScheduleManager scheduleManager;
    private DiscordHook discordHook;
    private WorldMenuGUI worldMenuGUI;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager     = new ConfigManager(this);
        this.langManager       = new LangManager(this);

        this.databaseManager   = new DatabaseManager(this);
        this.databaseManager.initialize();

        this.backupManager     = new BackupManager(this);
        this.worldRegenManager = new WorldRegenManager(this);
        this.scheduleManager   = new ScheduleManager(this);

        this.discordHook  = new DiscordHook(this);
        this.worldMenuGUI = new WorldMenuGUI(this);

        RegenCommand regenCommand = new RegenCommand(this);
        getCommand("ghostyregen").setExecutor(regenCommand);
        getCommand("ghostyregen").setTabCompleter(regenCommand);

        printBanner();
    }

    @Override
    public void onDisable() {
        if (scheduleManager != null) scheduleManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("GhostyWorldRegen has been disabled.");
    }

    private void printBanner() {
        // Use getPluginMeta() — non-deprecated since Paper 1.20+
        String version = getPluginMeta().getVersion();
        String name    = getPluginMeta().getName();
        String line    = "==============================================";
        getLogger().info(line);
        getLogger().info("         " + name + " v" + version + " enabled");
        getLogger().info("         Author >> Ger_Gh0stface");
        getLogger().info(line);
    }

    public void reload() {
        configManager.reload();
        langManager.reload();
        databaseManager.close();
        databaseManager.initialize();
        scheduleManager.reload();
    }

    // ── Getters ──────────────────────────────────────────────
    public static GhostyWorldRegen getInstance()     { return instance; }
    public ConfigManager     getConfigManager()      { return configManager; }
    public LangManager       getLangManager()        { return langManager; }
    public DatabaseManager   getDatabaseManager()    { return databaseManager; }
    public BackupManager     getBackupManager()      { return backupManager; }
    public WorldRegenManager getWorldRegenManager()  { return worldRegenManager; }
    public ScheduleManager   getScheduleManager()    { return scheduleManager; }
    public DiscordHook       getDiscordHook()        { return discordHook; }
    public WorldMenuGUI      getWorldMenuGUI()       { return worldMenuGUI; }
}
