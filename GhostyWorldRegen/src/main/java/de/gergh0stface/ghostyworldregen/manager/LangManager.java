package de.gergh0stface.ghostyworldregen.manager;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import de.gergh0stface.ghostyworldregen.util.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;

public class LangManager {

    private final GhostyWorldRegen plugin;
    private FileConfiguration lang;
    private String currentLang;

    public LangManager(GhostyWorldRegen plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        currentLang = plugin.getConfigManager().getLanguage();
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        // Save bundled lang files if they don't exist
        saveLangFile("en");
        saveLangFile("de");

        File langFile = new File(langFolder, currentLang + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + currentLang + ".yml' not found. Falling back to 'en'.");
            langFile = new File(langFolder, "en.yml");
        }

        lang = YamlConfiguration.loadConfiguration(langFile);

        // Supplement missing keys with defaults from bundled file
        InputStream defaultStream = plugin.getResource("lang/" + currentLang + ".yml");
        if (defaultStream == null) defaultStream = plugin.getResource("lang/en.yml");
        if (defaultStream != null) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            lang.setDefaults(defaults);
        }
    }

    private void saveLangFile(String locale) {
        File file = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
        if (!file.exists()) {
            plugin.saveResource("lang/" + locale + ".yml", false);
        }
    }

    public void reload() {
        load();
    }

    /**
     * Get a raw (uncolored) message from the language file.
     */
    public String getRaw(String key) {
        return lang.getString(key, "&cMissing lang key: " + key);
    }

    /**
     * Get a colorized message, replacing placeholders.
     * Placeholders are passed as alternating key-value pairs, e.g.:
     *   get("regen-start", "world", "my_world")
     */
    public String get(String key, String... placeholders) {
        String prefix = ColorUtil.colorize(plugin.getConfigManager().getPrefix());
        String msg = getRaw(key);

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }

        return prefix + " " + ColorUtil.colorize(msg);
    }

    /**
     * Send a language message to a CommandSender.
     */
    public void send(CommandSender sender, String key, String... placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    /**
     * Get a message without the prefix.
     */
    public String getNoPrefix(String key, String... placeholders) {
        String msg = getRaw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return ColorUtil.colorize(msg);
    }
}
