package com.anhnguyen.minekeo.commands;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import com.anhnguyen.minekeo.utils.LogManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

// cleaned unused imports
import java.util.UUID;
import java.util.HashMap;

public class MineKeo2FACommand implements CommandExecutor, TabCompleter {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    private static final HashMap<UUID, Long> unlinkCooldowns = new HashMap<>();
    
    public MineKeo2FACommand(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Cho phép console chạy reload, và tránh IndexOutOfBounds khi không có tham số
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getMessage("player-only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            // Show help
            showHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "link":
                handleLink(player);
                break;
            case "status":
                handleStatus(player);
                break;
            case "unlink":
                handleUnlink(player);
                break;
            case "debug":
                handleDebug(player);
                break;
            default:
                showHelp(player);
                break;
        }
        
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            java.util.List<String> subs = new java.util.ArrayList<>();
            subs.add("link");
            subs.add("status");
            subs.add("unlink");
            if (sender.hasPermission("minekeo2fa.admin")) {
                subs.add("debug");
                subs.add("reload");
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return java.util.Collections.emptyList();
    }

    private void showHelp(Player player) {
        player.sendMessage(config.getMessage("minekeo2fa-usage"));
        if (player.hasPermission("minekeo2fa.admin")) {
            java.util.List<String> adminHelp = config.getMessageList("minekeo2fa-admin-help");
            for (String line : adminHelp) {
                player.sendMessage(line);
            }
        }
    }
    
    private void handleLink(Player player) {
        if (plugin.getLinkManager().isLinked(player.getUniqueId())) {
            player.sendMessage(config.getMessage("already-linked"));
            return;
        }
        
        // Generate and send captcha
        plugin.getCaptchaManager().generateCaptcha(player);
        player.sendMessage(config.getMessage("captcha-generated"));
    }
    
    private void handleStatus(Player player) {
        send2FAStatus(player, plugin);
    }
    
    private void handleUnlink(Player player) {
        int delaySeconds = plugin.getConfig().getInt("unlink-delay-seconds", 300);
        long now = System.currentTimeMillis();
        Long lastUnlink = unlinkCooldowns.get(player.getUniqueId());
        if (lastUnlink != null && now - lastUnlink < delaySeconds * 1000L) {
            long remaining = (delaySeconds * 1000L - (now - lastUnlink)) / 1000L;
            player.sendMessage(config.getMessage("unlink-delay", "seconds", String.valueOf(remaining)));
            return;
        }
        unlinkCooldowns.put(player.getUniqueId(), now);
        String discordId = plugin.getLinkManager().getDiscordId(player.getUniqueId());
        if (discordId == null) {
            player.sendMessage(config.getMessage("not-linked"));
            return;
        }
        LogManager.info("Unlink request for player " + player.getName() + ", Discord ID: " + discordId);
        String unlinkId = UUID.randomUUID().toString();
        // Lưu pendingUnlinks để Discord xác nhận được
        plugin.getDiscordBotManager().getPendingUnlinks().put(unlinkId, new com.anhnguyen.minekeo.managers.DiscordBotManager.UnlinkData(player.getUniqueId(), player.getName(), unlinkId));
        plugin.getDiscordBotManager().sendUnlinkConfirmation(discordId, player.getName(), unlinkId);
        LogManager.info("Sending unlink confirmation to Discord for player " + player.getName());
        player.sendMessage(config.getMessage("unlink-confirm"));
    }
    
    private void handleDebug(Player player) {
        if (!player.hasPermission("minekeo2fa.admin")) {
            player.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
            return;
        }
        
        String playerIP = player.getAddress().getAddress().getHostAddress();
        boolean isStaff = player.hasPermission("minekeo2fa.staff") || player.isOp();
        boolean isStaffIP = plugin.getStaffIPManager().isStaffIP(playerIP);
        boolean staffIPEnabled = plugin.getConfigManager().isStaffIPEnabled();
        
        player.sendMessage("§8[§bMineKeo2FA§8] §e=== DEBUG INFO ===");
        player.sendMessage("§7Player: §e" + player.getName());
        player.sendMessage("§7IP: §e" + playerIP);
        player.sendMessage("§7Is Staff: §e" + isStaff);
        player.sendMessage("§7Is Staff IP: §e" + isStaffIP);
        player.sendMessage("§7Staff IP Check Enabled: §e" + staffIPEnabled);
        
        // Show staff IPs from config
        if (plugin.getConfig().contains("staff-ip.staffIPs")) {
            player.sendMessage("§7Staff IPs in config:");
            for (String staffName : plugin.getConfig().getConfigurationSection("staff-ip.staffIPs").getKeys(false)) {
                java.util.List<String> ips = plugin.getConfig().getStringList("staff-ip.staffIPs." + staffName);
                player.sendMessage("§7  " + staffName + ": §e" + ips);
            }
        } else {
            player.sendMessage("§7No staff IPs configured");
        }
        
        // Show punishment commands
        java.util.List<String> commands = plugin.getConfigManager().getStaffIPPunishCommands();
        player.sendMessage("§7Punishment commands: §e" + commands);
    }
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("minekeo2fa.admin") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
            return;
        }
        try {
            plugin.reloadConfig();
            plugin.getConfigManager().reloadLang();
            plugin.refreshRuntime();
            sender.sendMessage(config.getMessage("reload-success"));
            // Cleanup nhẹ sau reload
            plugin.getDiscordBotManager().cleanupStaleRequests(10 * 60 * 1000L);
        } catch (Exception e) {
            sender.sendMessage(config.getMessage("reload-fail", "error", e.getMessage()));
        }
    }
    
    public static void send2FAStatus(Player player, MineKeo2FA plugin) {
        ConfigManager config = plugin.getConfigManager();
        if (plugin.getLinkManager().isLinked(player.getUniqueId())) {
            // keep for future status detail if needed
            plugin.getLinkManager().isEnabled(player.getUniqueId());
            String discordId = plugin.getLinkManager().getDiscordId(player.getUniqueId());
            player.sendMessage(config.getMessage("status-discord-linked"));
            if (discordId != null) {
                try {
                    net.dv8tion.jda.api.entities.User discordUser = plugin.getJda().getUserById(discordId);
                    if (discordUser != null) {
                        String discordName = discordUser.getName();
                        player.sendMessage(config.getMessage("status-discord-info", "discord_name", discordName));
                    } else {
                        player.sendMessage(config.getMessage("status-discord-id", "discord_id", discordId));
                    }
                } catch (Exception e) {
                    player.sendMessage(config.getMessage("status-discord-id", "discord_id", discordId));
                }
            }
            String lastIP = plugin.getLinkManager().getLastIP(player.getUniqueId());
            if (lastIP != null) {
                player.sendMessage(config.getMessage("status-last-ip", "ip", lastIP));
            }
        } else {
            player.sendMessage(config.getMessage("status-not-linked"));
            player.sendMessage(config.getMessage("status-link-suggestion"));
        }
    }
}
