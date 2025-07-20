package com.anhnguyen.minekeo.managers;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import com.anhnguyen.minekeo.utils.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

public class StaffIPManager {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    private final Map<UUID, String> staffIPs; // Player UUID -> Expected IP
    
    public StaffIPManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.staffIPs = new HashMap<>();
        loadStaffIPs();
    }
    
    private void loadStaffIPs() {
        staffIPs.clear();
        // Không cần load IP vào Map vì isStaffIP đọc trực tiếp từ config
        // Chỉ log số lượng staff IPs được cấu hình
        int totalIPs = 0;
        if (plugin.getConfig().contains("staff-ip.staffIPs")) {
            for (String staffName : plugin.getConfig().getConfigurationSection("staff-ip.staffIPs").getKeys(false)) {
                List<String> ips = plugin.getConfig().getStringList("staff-ip.staffIPs." + staffName);
                totalIPs += ips.size();
            }
        }
        LogManager.info("Loaded " + totalIPs + " IPs for staff: " + plugin.getConfig().getString("staff-member-name", "staffMemberNameHere"));
    }
    
    public boolean isStaffIP(String ip) {
        if (!plugin.getConfig().contains("staff-ip.staffIPs")) {
            return false;
        }
        
        for (String staffName : plugin.getConfig().getConfigurationSection("staff-ip.staffIPs").getKeys(false)) {
            List<String> ips = plugin.getConfig().getStringList("staff-ip.staffIPs." + staffName);
            
            // Kiểm tra tất cả IP, không bỏ qua IP nào
            for (String staffIP : ips) {
                if (staffIP.trim().isEmpty()) {
                    continue; // Chỉ bỏ qua IP rỗng
                }
                
                if (staffIP.equals(ip)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public void checkStaffIP(Player player) {
        if (!plugin.getConfig().getBoolean("staff-ip.enabled", false)) {
            return;
        }
        
        // Check if there are any valid staff IPs configured
        if (!hasValidStaffIPs()) {
            return;
        }
        
        String playerIP = player.getAddress().getAddress().getHostAddress();
        String playerName = player.getName();
        
        // Check if player is staff
        if (isStaff(player)) {
            // Check if IP is in allowed list
            if (!isStaffIP(playerIP)) {
                plugin.getLogger().warning("Staff player " + playerName + " using unauthorized IP: " + playerIP);
                // Gửi cảnh báo lên Discord
                plugin.getDiscordBotManager().sendStaffIPAlert(playerName, playerIP);
                // Schedule punishment for staff using unauthorized IP
                schedulePunishment(player);
            }
        } else {
            // Check if non-staff player is using staff IP
            if (isStaffIP(playerIP)) {
                plugin.getLogger().warning("Non-staff player " + playerName + " using staff IP: " + playerIP);
                // Gửi cảnh báo lên Discord
                plugin.getDiscordBotManager().sendStaffIPAlert(playerName, playerIP);
                // Schedule punishment for non-staff using staff IP
                schedulePunishment(player);
            }
        }
    }
    
    private boolean isStaff(Player player) {
        // Implement staff detection logic
        // This could check permissions, groups, etc.
        return player.hasPermission("minekeo2fa.staff") || player.isOp();
    }
    
    private void schedulePunishment(Player player) {
        int delay = config.getStaffIPPunishDelay();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                executePunishment(player);
            }
        }, delay * 20L); // Convert seconds to ticks
    }
    
    private void executePunishment(Player player) {
        List<String> commands = config.getStaffIPPunishCommands();
        for (String command : commands) {
            // Thay thế placeholder %player% và {player}
            String processedCommand = command.replace("%player%", player.getName()).replace("{player}", player.getName());
            if (processedCommand.toLowerCase().startsWith("kick ")) {
                // Tách lý do kick
                String[] parts = processedCommand.split(" ", 3);
                if (parts.length >= 3) {
                    String reason = parts[2];
                    player.kickPlayer(reason);
                } else {
                    player.kickPlayer("");
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
        }
    }
    
    public void setStaffIP(UUID playerUUID, String ip) {
        staffIPs.put(playerUUID, ip);
    }
    
    public String getStaffIP(UUID playerUUID) {
        return staffIPs.get(playerUUID);
    }
    
    public void removeStaffIP(UUID playerUUID) {
        staffIPs.remove(playerUUID);
    }
    
    private boolean hasValidStaffIPs() {
        if (!plugin.getConfig().contains("staff-ip.staffIPs")) {
            return false;
        }
        
        for (String staffName : plugin.getConfig().getConfigurationSection("staff-ip.staffIPs").getKeys(false)) {
            List<String> ips = plugin.getConfig().getStringList("staff-ip.staffIPs." + staffName);
            for (String ip : ips) {
                // Kiểm tra tất cả IP, không bỏ qua IP nào
                if (!ip.trim().isEmpty()) {
                    return true; // Có ít nhất 1 IP
                }
            }
        }
        // Không có IP nào
        return false;
    }
} 