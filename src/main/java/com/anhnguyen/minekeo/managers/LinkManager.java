package com.anhnguyen.minekeo.managers;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LinkManager {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    private final Map<UUID, LinkData> playerLinks; // Minecraft UUID -> LinkData
    private final Map<String, List<UUID>> discordLinks; // Discord ID -> List of Minecraft UUIDs
    private final Map<UUID, String> recoveryCodes; // Minecraft UUID -> Recovery Code
    private final Gson gson;
    private final File dataFile;
    
    public LinkManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.playerLinks = new ConcurrentHashMap<>();
        this.discordLinks = new ConcurrentHashMap<>();
        this.recoveryCodes = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(plugin.getDataFolder(), "links.json");
        loadData();
    }
    
    public boolean linkAccount(UUID playerUUID, String discordId, String playerName) {
        // Check if player is already linked
        if (playerLinks.containsKey(playerUUID)) {
            return false;
        }
        
        // Check if Discord has reached max links
        List<UUID> linkedAccounts = discordLinks.getOrDefault(discordId, new ArrayList<>());
        if (linkedAccounts.size() >= config.getMaxLinksPerDiscord()) {
            return false;
        }
        
        // Generate recovery code
        String recoveryCode = generateRecoveryCode();
        
        // Create link data
        LinkData linkData = new LinkData(playerUUID, discordId, playerName, true, recoveryCode);
        
        // Add to maps
        playerLinks.put(playerUUID, linkData);
        linkedAccounts.add(playerUUID);
        discordLinks.put(discordId, linkedAccounts);
        recoveryCodes.put(playerUUID, recoveryCode);
        
        // Save data
        saveData();
        
        // Send recovery code to Discord
        plugin.getDiscordBotManager().sendRecoveryCode(discordId, playerName, recoveryCode);
        
        return true;
    }
    
    public boolean unlinkAccount(UUID playerUUID) {
        LinkData linkData = playerLinks.get(playerUUID);
        if (linkData == null) {
            return false;
        }
        
        // Remove from Discord links
        List<UUID> linkedAccounts = discordLinks.get(linkData.getDiscordId());
        if (linkedAccounts != null) {
            linkedAccounts.remove(playerUUID);
            if (linkedAccounts.isEmpty()) {
                discordLinks.remove(linkData.getDiscordId());
            }
        }
        
        // Remove from maps
        playerLinks.remove(playerUUID);
        recoveryCodes.remove(playerUUID);
        
        // Save data
        saveData();
        
        return true;
    }
    
    public boolean isLinked(UUID playerUUID) {
        return playerLinks.containsKey(playerUUID);
    }
    
    public boolean isEnabled(UUID playerUUID) {
        LinkData linkData = playerLinks.get(playerUUID);
        return linkData != null && linkData.isEnabled();
    }
    
    public void setEnabled(UUID playerUUID, boolean enabled) {
        LinkData linkData = playerLinks.get(playerUUID);
        if (linkData != null) {
            linkData.setEnabled(enabled);
            saveData();
        }
    }
    
    public boolean checkNewIP(UUID playerUUID, String currentIP) {
        LinkData linkData = playerLinks.get(playerUUID);
        if (linkData == null) {
            plugin.getLogger().info("checkNewIP: No link data for player " + playerUUID);
            return false;
        }
        
        String lastIP = linkData.getLastIP();
        plugin.getLogger().info("checkNewIP: Player " + playerUUID + " - Last IP: " + lastIP + ", Current IP: " + currentIP);
        
        if (lastIP == null) {
            // First time login, just update IP
            linkData.setLastIP(currentIP);
            saveData();
            plugin.getLogger().info("checkNewIP: First time login, updated IP to " + currentIP);
            return false;
        }
        
        if (!lastIP.equals(currentIP)) {
            // New IP detected
            linkData.setLastIP(currentIP);
            saveData();
            plugin.getLogger().info("checkNewIP: New IP detected! Changed from " + lastIP + " to " + currentIP);
            return true;
        }
        
        plugin.getLogger().info("checkNewIP: Same IP, no verification needed");
        return false;
    }
    
    public void updateLastIP(UUID playerUUID, String ip) {
        LinkData linkData = playerLinks.get(playerUUID);
        if (linkData != null) {
            linkData.setLastIP(ip);
            saveData();
        }
    }
    
    public String getLastIP(UUID playerUUID) {
        LinkData linkData = playerLinks.get(playerUUID);
        return linkData != null ? linkData.getLastIP() : null;
    }
    
    public String getDiscordId(UUID playerUUID) {
        LinkData linkData = playerLinks.get(playerUUID);
        return linkData != null ? linkData.getDiscordId() : null;
    }
    
    public List<UUID> getLinkedAccounts(String discordId) {
        return discordLinks.getOrDefault(discordId, new ArrayList<>());
    }
    
    public List<UUID> getAllLinkedAccounts() {
        return new ArrayList<>(playerLinks.keySet());
    }
    
    public String getPlayerName(UUID playerUUID) {
        LinkData linkData = playerLinks.get(playerUUID);
        return linkData != null ? linkData.getPlayerName() : null;
    }
    
    public String getRecoveryCode(UUID playerUUID) {
        return recoveryCodes.get(playerUUID);
    }
    
    public boolean validateRecoveryCode(UUID playerUUID, String code) {
        String storedCode = recoveryCodes.get(playerUUID);
        return storedCode != null && storedCode.equals(code);
    }
    
    public void updateRecoveryCode(UUID playerUUID) {
        String newCode = generateRecoveryCode();
        recoveryCodes.put(playerUUID, newCode);
        
        LinkData linkData = playerLinks.get(playerUUID);
        if (linkData != null) {
            linkData.setRecoveryCode(newCode);
            saveData();
            
            // Send new recovery code to Discord
            plugin.getDiscordBotManager().sendRecoveryCode(linkData.getDiscordId(), linkData.getPlayerName(), newCode);
        }
    }
    
    private String generateRecoveryCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int length = config.getRecoveryCodeLength();
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        
        for (int i = 0; i < length; i++) {
            code.append(characters.charAt(random.nextInt(characters.length())));
        }
        
        return code.toString();
    }
    
    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        
        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            
            // Load player links
            if (data.containsKey("playerLinks")) {
                Type linkType = new TypeToken<Map<String, LinkData>>(){}.getType();
                Map<String, LinkData> links = gson.fromJson(gson.toJson(data.get("playerLinks")), linkType);
                for (Map.Entry<String, LinkData> entry : links.entrySet()) {
                    playerLinks.put(UUID.fromString(entry.getKey()), entry.getValue());
                }
            }
            
            // Load Discord links
            if (data.containsKey("discordLinks")) {
                Type discordType = new TypeToken<Map<String, List<String>>>(){}.getType();
                Map<String, List<String>> discordData = gson.fromJson(gson.toJson(data.get("discordLinks")), discordType);
                for (Map.Entry<String, List<String>> entry : discordData.entrySet()) {
                    List<UUID> uuids = new ArrayList<>();
                    for (String uuidStr : entry.getValue()) {
                        uuids.add(UUID.fromString(uuidStr));
                    }
                    discordLinks.put(entry.getKey(), uuids);
                }
            }
            
            // Load recovery codes
            if (data.containsKey("recoveryCodes")) {
                Type recoveryType = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> codes = gson.fromJson(gson.toJson(data.get("recoveryCodes")), recoveryType);
                for (Map.Entry<String, String> entry : codes.entrySet()) {
                    recoveryCodes.put(UUID.fromString(entry.getKey()), entry.getValue());
                }
            }
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load link data: " + e.getMessage());
        }
    }
    
    public void saveData() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            
            Map<String, Object> data = new HashMap<>();
            
            // Save player links
            Map<String, LinkData> playerLinksData = new HashMap<>();
            for (Map.Entry<UUID, LinkData> entry : playerLinks.entrySet()) {
                playerLinksData.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("playerLinks", playerLinksData);
            
            // Save Discord links
            Map<String, List<String>> discordLinksData = new HashMap<>();
            for (Map.Entry<String, List<UUID>> entry : discordLinks.entrySet()) {
                List<String> uuids = new ArrayList<>();
                for (UUID uuid : entry.getValue()) {
                    uuids.add(uuid.toString());
                }
                discordLinksData.put(entry.getKey(), uuids);
            }
            data.put("discordLinks", discordLinksData);
            
            // Save recovery codes
            Map<String, String> recoveryCodesData = new HashMap<>();
            for (Map.Entry<UUID, String> entry : recoveryCodes.entrySet()) {
                recoveryCodesData.put(entry.getKey().toString(), entry.getValue());
            }
            data.put("recoveryCodes", recoveryCodesData);
            
            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(data, writer);
            }
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save link data: " + e.getMessage());
        }
    }
    
    public static class LinkData {
        private UUID playerUUID;
        private String discordId;
        private String playerName;
        private boolean enabled;
        private String recoveryCode;
        private String lastIP;
        
        // Constructor mặc định cho Gson
        public LinkData() {}
        
        public LinkData(UUID playerUUID, String discordId, String playerName, boolean enabled, String recoveryCode) {
            this.playerUUID = playerUUID;
            this.discordId = discordId;
            this.playerName = playerName;
            this.enabled = enabled;
            this.recoveryCode = recoveryCode;
            this.lastIP = null;
        }
        
        public LinkData(UUID playerUUID, String discordId, String playerName, boolean enabled, String recoveryCode, String lastIP) {
            this.playerUUID = playerUUID;
            this.discordId = discordId;
            this.playerName = playerName;
            this.enabled = enabled;
            this.recoveryCode = recoveryCode;
            this.lastIP = lastIP;
        }
        
        public UUID getPlayerUUID() { return playerUUID; }
        public void setPlayerUUID(UUID playerUUID) { this.playerUUID = playerUUID; }
        public String getDiscordId() { return discordId; }
        public void setDiscordId(String discordId) { this.discordId = discordId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getRecoveryCode() { return recoveryCode; }
        public void setRecoveryCode(String recoveryCode) { this.recoveryCode = recoveryCode; }
        public String getLastIP() { return lastIP; }
        public void setLastIP(String lastIP) { this.lastIP = lastIP; }
    }
} 