package de.gergh0stface.ghostyworldregen.util;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {}

    /**
     * Translates both legacy & color codes and &#RRGGBB hex codes into colored strings.
     *
     * @param text The raw text with color codes
     * @return The colorized string
     */
    public static String colorize(String text) {
        if (text == null) return "";

        // Replace &#RRGGBB hex codes
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = "#" + matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of(hex).toString());
        }
        matcher.appendTail(buffer);

        // Translate legacy & codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Strip all color codes from a string.
     */
    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }
}
