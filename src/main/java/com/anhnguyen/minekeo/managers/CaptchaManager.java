package com.anhnguyen.minekeo.managers;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CaptchaManager {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    private final Map<String, CaptchaData> captchaMap;
    private final Random random;
    
    public CaptchaManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.captchaMap = new HashMap<>();
        this.random = new Random();
    }
    
    public String generateCaptcha(Player player) {
        String captcha = generateRandomCaptcha();
        long expireTime = System.currentTimeMillis() + (config.getCaptchaExpireTime() * 1000L);
        
        CaptchaData data = new CaptchaData(player.getUniqueId(), captcha, expireTime);
        captchaMap.put(captcha, data);

        String guildName = plugin.getDiscordBotManager().getCachedGuildName();
        String channelName = plugin.getDiscordBotManager().getCachedChannelName();
        if (guildName != null && channelName != null) {
            player.sendMessage(config.getMessage("captcha-guild-info", "channel", channelName, "guild", guildName));
        } else {
            player.sendMessage(config.getMessage("captcha-guild-info"));
        }
        String padding = String.join("", java.util.Collections.nCopies(20, " "));

        TextComponent suggestMsg = new TextComponent(padding + "§eBấm vào đây để copy mã captcha!");
        suggestMsg.setClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                captcha
        ));
        player.sendMessage(" ");
        player.sendMessage(" ");
        player.sendMessage(" ");
        player.sendMessage(" ");
        player.spigot().sendMessage(suggestMsg);
        player.spigot().sendMessage(new TextComponent(config.getMessage("captcha-warning")));
        player.sendMessage(" ");
        player.sendMessage(" ");
        player.sendMessage(" ");
        player.sendMessage(" ");
        return captcha;
    }
    
    private String generateRandomCaptcha() {
        String characters = config.getCaptchaCharacters();
        int length = config.getCaptchaLength();
        StringBuilder captcha = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            captcha.append(characters.charAt(random.nextInt(characters.length())));
        }
        
        return captcha.toString();
    }
    
    public boolean validateCaptcha(String captcha, UUID playerUUID) {
        CaptchaData data = captchaMap.get(captcha);
        if (data == null) {
            return false;
        }
        
        // Check if expired
        if (System.currentTimeMillis() > data.getExpireTime()) {
            captchaMap.remove(captcha);
            return false;
        }
        
        // Check if belongs to player
        if (!data.getPlayerUUID().equals(playerUUID)) {
            return false;
        }
        
        // Remove from map after successful validation
        captchaMap.remove(captcha);
        return true;
    }
    
    public boolean isCaptchaValid(String captcha) {
        CaptchaData data = captchaMap.get(captcha);
        if (data == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > data.getExpireTime()) {
            captchaMap.remove(captcha);
            return false;
        }
        
        return true;
    }
    
    public void removeCaptcha(String captcha) {
        captchaMap.remove(captcha);
    }
    
    public void cleanupExpiredCaptchas() {
        long currentTime = System.currentTimeMillis();
        captchaMap.entrySet().removeIf(entry -> entry.getValue().getExpireTime() < currentTime);
    }
    
    public CaptchaData getCaptchaData(String captcha) {
        return captchaMap.get(captcha);
    }
    
    public static class CaptchaData {
        private final UUID playerUUID;
        private final String captcha;
        private final long expireTime;
        
        public CaptchaData(UUID playerUUID, String captcha, long expireTime) {
            this.playerUUID = playerUUID;
            this.captcha = captcha;
            this.expireTime = expireTime;
        }
        
        public UUID getPlayerUUID() {
            return playerUUID;
        }
        
        public String getCaptcha() {
            return captcha;
        }
        
        public long getExpireTime() {
            return expireTime;
        }
    }
} 