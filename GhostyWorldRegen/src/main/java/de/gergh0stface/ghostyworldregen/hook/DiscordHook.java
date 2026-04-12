package de.gergh0stface.ghostyworldregen.hook;

import de.gergh0stface.ghostyworldregen.GhostyWorldRegen;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

/**
 * Sends embed notifications to a Discord webhook.
 * Configure the webhook URL in config.yml under discord.webhook-url.
 */
public class DiscordHook {

    private final GhostyWorldRegen plugin;
    private final HttpClient httpClient;

    public DiscordHook(GhostyWorldRegen plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false)
                && !getWebhookUrl().isEmpty();
    }

    private String getWebhookUrl() {
        return plugin.getConfig().getString("discord.webhook-url", "");
    }

    // ── Events ────────────────────────────────────────────────

    public void sendRegenStarted(String world, String triggeredBy) {
        if (!isEnabled()) return;
        sendEmbed(
                "🔄 World Regeneration Started",
                "The world **" + world + "** is being regenerated.",
                0xFFA500,
                field("World", world, "true"),
                field("Triggered by", triggeredBy, "true"),
                field("Server", Bukkit.getServer().getName(), "true")
        );
    }

    public void sendRegenSuccess(String world, String triggeredBy, long durationMs) {
        if (!isEnabled()) return;
        sendEmbed(
                "✅ World Regeneration Complete",
                "The world **" + world + "** was successfully regenerated.",
                0x2ECC71,
                field("World", world, "true"),
                field("Triggered by", triggeredBy, "true"),
                field("Duration", formatDuration(durationMs), "true")
        );
    }

    public void sendRegenFailed(String world, String reason) {
        if (!isEnabled()) return;
        sendEmbed(
                "❌ World Regeneration Failed",
                "The regeneration of **" + world + "** encountered an error.",
                0xE74C3C,
                field("World", world, "true"),
                field("Reason", reason, "false")
        );
    }

    public void sendBackupCreated(String world, String fileName) {
        if (!isEnabled()) return;
        if (!plugin.getConfig().getBoolean("discord.notify-backups", true)) return;
        sendEmbed(
                "💾 Backup Created",
                "A backup for world **" + world + "** has been created.",
                0x3498DB,
                field("World", world, "true"),
                field("File", fileName, "false")
        );
    }

    public void sendScheduledRegen(String world, int intervalHours) {
        if (!isEnabled()) return;
        sendEmbed(
                "⏰ Scheduled Regen Triggered",
                "Auto-regeneration started for **" + world + "** (every " + intervalHours + "h).",
                0x9B59B6,
                field("World", world, "true"),
                field("Interval", intervalHours + "h", "true"),
                field("Time", new SimpleDateFormat("HH:mm:ss").format(new Date()), "true")
        );
    }

    // ── Builder helpers ───────────────────────────────────────

    /** Creates a field triplet: {name, value, inline}. inline must be "true" or "false". */
    private String[] field(String name, String value, String inline) {
        return new String[]{name, value, inline};
    }

    // ── HTTP ──────────────────────────────────────────────────

    private void sendEmbed(String title, String description, int color, String[]... fields) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StringBuilder fieldJson = new StringBuilder();
                for (String[] f : fields) {
                    if (fieldJson.length() > 0) fieldJson.append(",");
                    fieldJson.append("{")
                            .append("\"name\":\"").append(escape(f[0])).append("\",")
                            .append("\"value\":\"").append(escape(f[1])).append("\",")
                            .append("\"inline\":").append(f[2])   // already "true"/"false"
                            .append("}");
                }

                String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date());
                String footer = "GhostyWorldRegen v"
                        + plugin.getPluginMeta().getVersion();

                String payload = "{"
                        + "\"username\":\"GhostyWorldRegen\","
                        + "\"embeds\":[{"
                        + "\"title\":\"" + escape(title) + "\","
                        + "\"description\":\"" + escape(description) + "\","
                        + "\"color\":" + color + ","
                        + "\"fields\":[" + fieldJson + "],"
                        + "\"footer\":{\"text\":\"" + escape(footer) + "\"},"
                        + "\"timestamp\":\"" + timestamp + "\""
                        + "}]}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(getWebhookUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() != 204 && response.statusCode() != 200) {
                    plugin.getLogger().warning("[Discord] Webhook returned HTTP " + response.statusCode());
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to send webhook: " + e.getMessage());
            }
        });
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
