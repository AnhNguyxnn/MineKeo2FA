package com.anhnguyen.minekeo.utils;

import com.anhnguyen.minekeo.MineKeo2FA;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {
    
    private final MineKeo2FA plugin;
    private final FileConfiguration config;
    
    public ConfigManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        plugin.saveDefaultConfig();
    }
    
    public String getMessage(String path) {
        String message = config.getString("messages." + path);
        if (message == null) {
            return "Message not found: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', getPrefix() + message);
    }
    
    public String getMessage(String path, String... placeholders) {
        String message = getMessage(path);
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String key = placeholders[i];
                String value = placeholders[i + 1];
                String placeholder = "%" + key.replaceAll("[{}%]", "") + "%";
                message = message.replace(placeholder, value);
            }
        }
        return message;
    }
    
    public List<String> getMessageList(String path) {
        List<String> messages = config.getStringList("messages." + path);
        messages.replaceAll(msg -> ChatColor.translateAlternateColorCodes('&', msg));
        return messages;
    }
    
    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', config.getString("messages.prefix", "&8[&bMineKeo2FA&8] &r"));
    }
    
    public String getDiscordToken() {
        return config.getString("discord.token");
    }
    
    public String getGuildId() {
        return config.getString("discord.guild-id", "YOUR_GUILD_ID_HERE");
    }
    
    public String getCaptchaChannel() {
        return config.getString("discord.captcha-channel");
    }
    
    public String getCaptchaChannelId() {
        return config.getString("discord.captcha-channel-id");
    }
    
    public String getEmbedChannelId() {
        return config.getString("discord.embed-channel-id");
    }
    
    public int getCaptchaLength() {
        return config.getInt("captcha.length", 6);
    }
    
    public String getCaptchaCharacters() {
        return config.getString("captcha.characters", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    }
    
    public int getCaptchaExpireTime() {
        return config.getInt("security.captcha-expire-time", 300);
    }
    
    public int getMaxLinksPerDiscord() {
        return config.getInt("linking.max-links-per-discord", 3);
    }
    
    public int getRecoveryCodeLength() {
        return config.getInt("linking.recovery-code-length", 8);
    }
    
    public boolean isSessionEnabled() {
        return config.getBoolean("session.enabled", true);
    }
    
    public boolean isSessionTimeEnabled() {
        return config.getBoolean("session.session-time-enabled", false);
    }
    
    public long getSessionTime() {
        return config.getLong("session.session-time", 86400);
    }
    
    public boolean isBlockingEnabled(String setting) {
        return config.getBoolean("blocking-settings." + setting, true);
    }
    
    public boolean isStaffIPEnabled() {
        return config.getBoolean("staff-ip.enabled", true);
    }
    
    public List<String> getStaffIPs() {
        return config.getStringList("staff-ip.staff-ips");
    }
    
    public List<String> getStaffIPPunishCommands() {
        return config.getStringList("staff-ip.punish.staff-ip-not-verified");
    }
    
    public int getStaffIPPunishDelay() {
        return config.getInt("staff-ip.punish.delay", 10);
    }
    
    public String getOnlineStatus() {
        return config.getString("discord.online-status", "IDLE");
    }
    
    public String getActivity() {
        return config.getString("discord.activity", "CUSTOM");
    }
    
    public List<String> getActivityMessages() {
        return config.getStringList("discord.activity-message");
    }
    
    public String getBotToken() {
        return config.getString("discord.bot-token", "YOUR_BOT_TOKEN_HERE");
    }
    
    public String getStaffAlertChannelId() {
        return config.getString("discord.staff-alert-channel-id", "YOUR_STAFF_ALERT_CHANNEL_ID_HERE");
    }
    
    public String getOp2FARequiredMessage() {
        return config.getString("messages.op-2fa-required", "§c⚠️ Bạn có quyền OP nhưng chưa bật 2FA!");
    }
    
    public String getOp2FAFrozenMessage() {
        return config.getString("messages.op-2fa-frozen", "§cBạn sẽ bị đóng băng cho đến khi hoàn thành 2FA!");
    }
    
    public int getSessionDuration() {
        return config.getInt("security.session-duration", 30);
    }
    
    public int getFreezeDuration() {
        return config.getInt("security.freeze-duration", 60);
    }
    
    public String getDiscordMessage(String path) {
        String message = config.getString("discord-messages." + path);
        if (message == null) {
            return "Message not found: " + path;
        }
        return message;
    }
    
    public String getDiscordMessage(String path, String... placeholders) {
        String message = getDiscordMessage(path);
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String key = placeholders[i];
                String value = placeholders[i + 1];
                String placeholder = "%" + key.replaceAll("[{}%]", "") + "%";
                message = message.replace(placeholder, value);
            }
        }
        return message;
    }
    
    public MineKeo2FA getPlugin() {
        return plugin;
    }
} 