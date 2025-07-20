package com.anhnguyen.minekeo.managers;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    private final Map<UUID, SessionData> sessions;
    private final Gson gson;
    private final File dataFile;
    
    public SessionManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.sessions = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(plugin.getDataFolder(), "sessions.json");
        loadData();
    }
    
    public void createSession(UUID playerUUID) {
        long expireTime = config.isSessionTimeEnabled() ? 
            System.currentTimeMillis() + (config.getSessionTime() * 1000L) : 
            Long.MAX_VALUE;
        
        SessionData session = new SessionData(playerUUID, true, expireTime);
        sessions.put(playerUUID, session);
        saveData();
    }
    
    public boolean hasValidSession(UUID playerUUID) {
        if (!config.isSessionEnabled()) {
            return false;
        }
        
        SessionData session = sessions.get(playerUUID);
        if (session == null) {
            return false;
        }
        
        // Check if session is expired
        if (config.isSessionTimeEnabled() && System.currentTimeMillis() > session.getExpireTime()) {
            sessions.remove(playerUUID);
            return false;
        }
        
        return session.isVerified();
    }
    
    public void setVerified(UUID playerUUID, boolean verified) {
        SessionData session = sessions.get(playerUUID);
        if (session != null) {
            session.setVerified(verified);
            saveData();
        }
    }
    
    public void removeSession(UUID playerUUID) {
        sessions.remove(playerUUID);
        saveData();
    }
    
    public void cleanupExpiredSessions() {
        if (!config.isSessionTimeEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().getExpireTime() < currentTime);
        saveData();
    }
    
    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        
        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, SessionData>>(){}.getType();
            Map<String, SessionData> data = gson.fromJson(reader, type);
            
            for (Map.Entry<String, SessionData> entry : data.entrySet()) {
                sessions.put(UUID.fromString(entry.getKey()), entry.getValue());
            }
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load session data: " + e.getMessage());
        }
    }
    
    public void saveData() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            
            Map<String, SessionData> data = new HashMap<>();
            for (Map.Entry<UUID, SessionData> entry : sessions.entrySet()) {
                data.put(entry.getKey().toString(), entry.getValue());
            }
            
            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(data, writer);
            }
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save session data: " + e.getMessage());
        }
    }
    
    public static class SessionData {
        private final UUID playerUUID;
        private boolean verified;
        private final long expireTime;
        
        public SessionData(UUID playerUUID, boolean verified, long expireTime) {
            this.playerUUID = playerUUID;
            this.verified = verified;
            this.expireTime = expireTime;
        }
        
        public UUID getPlayerUUID() {
            return playerUUID;
        }
        
        public boolean isVerified() {
            return verified;
        }
        
        public void setVerified(boolean verified) {
            this.verified = verified;
        }
        
        public long getExpireTime() {
            return expireTime;
        }
    }
} 