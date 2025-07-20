package com.anhnguyen.minekeo.listeners;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import com.nickuc.login.api.event.bukkit.auth.AuthenticateEvent;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.List;
import org.bukkit.event.player.PlayerJoinEvent;


public class NLoginListener implements Listener {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    
    public NLoginListener(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(AuthenticateEvent event) {
        Player player = event.getPlayer();
        
        // Delay để đảm bảo player đã hoàn toàn login và online
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    plugin.getLogger().info("Player " + player.getName() + " is not online, skipping 2FA check");
                    return; // Player đã offline
                }
                
                handlePlayerAuth(player);
            }
        }.runTaskLater(plugin, 20L); // Delay 1 second
    }

//    @EventHandler(priority = EventPriority.HIGH)
//    public void onPlayerJoin(PlayerJoinEvent event) {
//        Player player = event.getPlayer();
//        new org.bukkit.scheduler.BukkitRunnable() {
//            @Override
//            public void run() {
//                if (!player.isOnline()) return;
//                handlePlayerAuth(player);
//            }
//        }.runTaskLater(plugin, 20L);
//    }
    
    private void handlePlayerAuth(Player player) {
        // Kiểm tra IP blacklist
        List<String> blacklist = plugin.getConfig().getStringList("ip-blacklist");
        if (isBlacklisted(player.getAddress().getAddress().getHostAddress(), blacklist)) {
            String punishMsg = plugin.getConfig().getString("blacklist-ip-punish", "§cIP của bạn đã bị cấm truy cập máy chủ!");
            player.kickPlayer(punishMsg);
            Bukkit.getBanList(BanList.Type.NAME).addBan(
                    player.getName(),                              // target: tên người chơi
                    config.getMessage("ban-blacklist-ip"),     // reason: lý do cấm (có thể có màu)
                    null,                                          // expires: null = vĩnh viễn
                    plugin.getDescription().getName()             // source: tên plugin
            );
            // Gửi cảnh báo lên Discord
            plugin.getDiscordBotManager().sendBlacklistAlert(player.getName(), player.getAddress().getAddress().getHostAddress());
            return;
        }

        // Check staff IP
        plugin.getStaffIPManager().checkStaffIP(player);

        // Lấy IP hiện tại
        String currentIP = player.getAddress().getAddress().getHostAddress();
        String lastVerifiedIP = plugin.getLinkManager().getLastIP(player.getUniqueId());
        boolean has2FA = plugin.getLinkManager().isEnabled(player.getUniqueId());

        // Nếu có 2FA
        if (has2FA) {
            // Nếu IP khác với IP đã xác minh cuối cùng, luôn gửi 2FA
            if (lastVerifiedIP == null || !lastVerifiedIP.equals(currentIP)) {
                plugin.getFreezeManager().freezePlayer(player);
                plugin.getDiscordBotManager().sendVerificationRequest(player);
                player.sendMessage(config.getMessage("2fa-verification-required"));
            } else {
                // Nếu IP giống, cho vào game bình thường, tạo session nếu chưa có
                if (!plugin.getSessionManager().hasValidSession(player.getUniqueId())) {
                    plugin.getSessionManager().createSession(player.getUniqueId());
                }
            }
        } else {
            // Nếu chưa bật 2FA, gợi ý bật
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.sendMessage(config.getMessage("2fa-suggestion"));
                    }
                }
            }.runTaskLater(plugin, 20L);
        }

        // Nếu là OP mà chưa bật 2FA thì đóng băng
        if (player.isOp() && !plugin.getLinkManager().isEnabled(player.getUniqueId())) {
            plugin.getFreezeManager().freezePlayer(player);
            player.sendMessage(config.getOp2FARequiredMessage());
            player.sendMessage(config.getOp2FAFrozenMessage());
        }
    }

    private boolean isBlacklisted(String ip, List<String> blacklist) {
        for (String entry : blacklist) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            if (entry.contains("/")) {
                if (isInCIDR(ip, entry)) return true;
            } else {
                if (ip.equals(entry)) return true;
            }
        }
        return false;
    }

    private boolean isInCIDR(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String cidrIp = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            long ipLong = ipToLong(ip);
            long cidrLong = ipToLong(cidrIp);
            int shift = 32 - prefix;
            return (ipLong >> shift) == (cidrLong >> shift);
        } catch (Exception e) {
            return false;
        }
    }

    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long res = 0;
        for (int i = 0; i < 4; i++) {
            res = (res << 8) + Integer.parseInt(octets[i]);
        }
        return res;
    }
}