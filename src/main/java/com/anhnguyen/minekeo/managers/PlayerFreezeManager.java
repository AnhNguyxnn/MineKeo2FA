package com.anhnguyen.minekeo.managers;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerFreezeManager {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    private final Set<UUID> frozenPlayers;
    
    public PlayerFreezeManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.frozenPlayers = new HashSet<>();
    }
    
    public void freezePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        frozenPlayers.add(playerUUID);
        
        // Apply blindness effect
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        
        // Hide other players
        if (config.isBlockingEnabled("hide-other-on-entering")) {
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.equals(player)) {
                    player.hidePlayer(other);
                }
            }
        }
        
        // Send freeze message
        player.sendMessage(config.getMessage("frozen"));
        
        // Gửi title (Spigot 1.8.8 không hỗ trợ sendTitle, chỉ gửi message)
        // player.sendTitle(
        //     config.getMessage("frozen").replace("&", "§"),
        //     "§7Vui lòng xác minh qua Discord",
        //     10, 70, 20
        // );
    }
    
    public void unfreezePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        frozenPlayers.remove(playerUUID);
        
        // Remove blindness effect
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        
        // Show other players
        if (config.isBlockingEnabled("hide-other-on-entering")) {
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.equals(player)) {
                    player.showPlayer(other);
                }
            }
        }
        
        // Send success message
        player.sendMessage(config.getMessage("verification-success"));
        
        // Gửi title (Spigot 1.8.8 không hỗ trợ sendTitle, chỉ gửi message)
        // player.sendTitle(
        //     "§aXác minh thành công!",
        //     "§7Chào mừng trở lại",
        //     10, 70, 20
        // );
    }
    
    public boolean isFrozen(UUID playerUUID) {
        return frozenPlayers.contains(playerUUID);
    }
    
    public boolean isFrozen(Player player) {
        return isFrozen(player.getUniqueId());
    }
    
    public void removeFrozenPlayer(UUID playerUUID) {
        frozenPlayers.remove(playerUUID);
    }
    
    public Set<UUID> getFrozenPlayers() {
        return new HashSet<>(frozenPlayers);
    }
    
    public void clearAllFrozenPlayers() {
        frozenPlayers.clear();
    }
} 