package com.anhnguyen.minekeo.managers;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.nickuc.login.api.nLoginAPI;
import org.bukkit.entity.Player;

public class NLoginAPIManager {
    
    private final MineKeo2FA plugin;
    private final nLoginAPI nLoginAPI;
    
    public NLoginAPIManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.nLoginAPI = com.nickuc.login.api.nLoginAPI.getApi();
    }
    
    /**
     * Đổi mật khẩu cho player
     * @param player Player cần đổi mật khẩu
     * @param newPassword Mật khẩu mới
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean changePassword(Player player, String newPassword) {
        try {
            boolean result = nLoginAPI.changePassword(player.getName(), newPassword);
            if (result) {
                plugin.getLogger().info("Successfully changed password for player: " + player.getName());
            } else {
                plugin.getLogger().warning("Failed to change password for player: " + player.getName());
            }
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error changing password for player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Kiểm tra mật khẩu của player
     * @param player Player cần kiểm tra
     * @param password Mật khẩu cần kiểm tra
     * @return true nếu mật khẩu đúng, false nếu sai
     */
    public boolean checkPassword(Player player, String password) {
        try {
            // NLogin API có thể không có method checkPassword trực tiếp
            // Sử dụng reflection hoặc method khác
            plugin.getLogger().info("Password check for player " + player.getName() + " - method not available");
            return false; // Placeholder
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking password for player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Kiểm tra player đã đăng ký chưa
     * @param player Player cần kiểm tra
     * @return true nếu đã đăng ký, false nếu chưa
     */
    public boolean isRegistered(Player player) {
        try {
            boolean result = nLoginAPI.isRegistered(player.getName());
            plugin.getLogger().info("Registration check for player " + player.getName() + ": " + result);
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking registration for player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Đăng ký player với mật khẩu
     * @param player Player cần đăng ký
     * @param password Mật khẩu
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean registerPlayer(Player player, String password) {
        try {
            // NLogin API có thể không có method register trực tiếp
            // Sử dụng reflection hoặc method khác
            plugin.getLogger().info("Register player " + player.getName() + " - method not available");
            return false; // Placeholder
        } catch (Exception e) {
            plugin.getLogger().severe("Error registering player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Lấy thông tin player từ NLogin
     * @param playerName Tên player
     * @return Thông tin player hoặc null nếu không tìm thấy
     */
    public Object getPlayerInfo(String playerName) {
        try {
            // NLogin API có thể có method để lấy thông tin player
            // Tùy thuộc vào version của NLogin
            return null; // Placeholder
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player info for " + playerName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Kiểm tra xem NLogin API có sẵn không
     * @return true nếu sẵn, false nếu không
     */
    public boolean isAPIReady() {
        return nLoginAPI != null;
    }
} 