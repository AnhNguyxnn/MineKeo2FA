package com.anhnguyen.minekeo.commands;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BaomatCommand implements CommandExecutor {
    private final MineKeo2FA plugin;
    private final ConfigManager config;

    public BaomatCommand(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        if (args.length > 0) {
            return true;
        }
        Player player = (Player) sender;
        if (plugin.getLinkManager().isLinked(player.getUniqueId())) {
            player.sendMessage(config.getMessage("already-linked"));
            return true;
        }
        String captcha = plugin.getCaptchaManager().generateCaptcha(player);
        player.sendMessage(config.getMessage("captcha-generated"));
        return true;
    }
} 