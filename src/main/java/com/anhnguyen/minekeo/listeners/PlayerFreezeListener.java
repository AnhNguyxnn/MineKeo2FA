package com.anhnguyen.minekeo.listeners;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

public class PlayerFreezeListener implements Listener {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    
    public PlayerFreezeListener(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getFreezeManager().isFrozen(player)) {
            // Allow looking around but not moving
            if (event.getFrom().getX() != event.getTo().getX() || 
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player) && config.isBlockingEnabled("block-item-drop")) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player) && config.isBlockingEnabled("block-item-pickup")) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();
        
        if (plugin.getFreezeManager().isFrozen(player) && config.isBlockingEnabled("block-commands")) {
            // Lấy danh sách lệnh cho phép từ config (nếu có)
            java.util.List<String> allowed = plugin.getConfig().getStringList("blocking-settings.allowed-commands");
            boolean allowedCommand = false;
            for (String allow : allowed) {
                if (command.startsWith(allow.toLowerCase())) {
                    allowedCommand = true;
                    break;
                }
            }
            if (!allowedCommand) {
                event.setCancelled(true);
                player.sendMessage(config.getMessage("frozen"));
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFreezeManager().isFrozen(player)) {
            event.setCancelled(true);
            player.sendMessage(config.getMessage("frozen"));
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getFreezeManager().isFrozen(player) && config.isBlockingEnabled("block-damage")) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamageEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (plugin.getFreezeManager().isFrozen(player) && config.isBlockingEnabled("block-damaging-entity")) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (plugin.getFreezeManager().isFrozen(player) && config.isBlockingEnabled("block-inventory-open")) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (plugin.getFreezeManager().isFrozen(player) && config.isBlockingEnabled("block-inventory-open")) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Remove from frozen players when they quit
        if (plugin.getFreezeManager().isFrozen(player)) {
            plugin.getFreezeManager().removeFrozenPlayer(player.getUniqueId());
        }
    }
} 