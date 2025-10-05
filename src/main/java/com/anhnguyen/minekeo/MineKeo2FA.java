package com.anhnguyen.minekeo;

import com.anhnguyen.minekeo.commands.MineKeo2FACommand;
import com.anhnguyen.minekeo.listeners.NLoginListener;
import com.anhnguyen.minekeo.listeners.PlayerFreezeListener;
import com.anhnguyen.minekeo.managers.*;
import com.anhnguyen.minekeo.utils.ConfigManager;
import com.anhnguyen.minekeo.utils.LogManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;
import java.util.List;
import java.util.Random;

public final class MineKeo2FA extends JavaPlugin {

    private static MineKeo2FA instance;
    private JDA jda;
    private ConfigManager configManager;
    private CaptchaManager captchaManager;
    private LinkManager linkManager;
    private SessionManager sessionManager;
    private PlayerFreezeManager freezeManager;
    private StaffIPManager staffIPManager;
    private DiscordBotManager discordBotManager;
    private NLoginAPIManager nLoginAPIManager;
    private Random activityRandom = new Random();
    private int activityTaskId = -1;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize custom logging system
        LogManager.initialize(this);
        
        LogManager.info("=== MineKeo2FA v" + getDescription().getVersion() + " Starting ===");
        
        // Check if NLogin is available
        if (getServer().getPluginManager().getPlugin("nLogin") == null) {
            LogManager.severe("NLogin plugin not found! MineKeo2FA requires NLogin to function properly.");
            LogManager.severe("Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize managers
        configManager = new ConfigManager(this);
        captchaManager = new CaptchaManager(this);
        linkManager = new LinkManager(this);
        sessionManager = new SessionManager(this);
        freezeManager = new PlayerFreezeManager(this);
        staffIPManager = new StaffIPManager(this);
        discordBotManager = new DiscordBotManager(this);
        nLoginAPIManager = new NLoginAPIManager(this);
        
        // Initialize Discord bot
        if (!initializeDiscordBot()) {
            LogManager.severe("Failed to initialize Discord bot! Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Delay 2 giây để JDA cache xong user/guild trước khi đăng ký listener và cho phép gửi tin nhắn
        getServer().getScheduler().runTaskLater(this, () -> {
            // Register commands
            registerCommands();
            // Register listeners
            registerListeners();
            LogManager.info("=== MineKeo2FA v" + getDescription().getVersion() + " Started Successfully ===");
            LogManager.info("NLogin integration: ENABLED");
        }, 40L); // 2 giây
    }

    @Override
    public void onDisable() {
        LogManager.info("=== MineKeo2FA v" + getDescription().getVersion() + " Shutting Down ===");
        
        // Shutdown Discord bot
        if (jda != null) {
            jda.shutdown();
        }
        
        // Save all data
        if (linkManager != null) {
            linkManager.saveData();
        }
        if (sessionManager != null) {
            sessionManager.saveData();
        }
        
        if (activityTaskId != -1) getServer().getScheduler().cancelTask(activityTaskId);
        
        // Close custom logging system
        LogManager.close();
        
        LogManager.info("=== MineKeo2FA v" + getDescription().getVersion() + " Shutdown Complete ===");
    }

    private boolean initializeDiscordBot() {
        String token = getConfig().getString("discord.bot-token");
        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE")) {
            LogManager.severe("Discord bot token not configured! Please set it in config.yml");
            return false;
        }
        
        try {
            List<String> activities = getConfig().getStringList("discord.activities");
            if (activities == null || activities.isEmpty()) {
                activities = java.util.Arrays.asList("Bảo mật tài khoản...");
            }
            int interval = getConfig().getInt("discord.activity-interval", 300);
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS
                    )
                    .setActivity(Activity.playing(activities.get(0)))
                    .build();
            jda.awaitReady();
            
            // Initialize Discord bot manager
            discordBotManager.initialize(jda);
            
            LogManager.info("Discord bot initialized successfully!");
            // Schedule random activity change
            if (activityTaskId != -1) getServer().getScheduler().cancelTask(activityTaskId);
            final List<String> activitiesFinal = activities;
            activityTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
                if (jda != null && !activitiesFinal.isEmpty()) {
                    String act = activitiesFinal.get(activityRandom.nextInt(activitiesFinal.size()));
                    jda.getPresence().setActivity(Activity.playing(act));
                }
            }, interval * 20L, interval * 20L);
            return true;
        } catch (Exception e) {
            LogManager.severe("Failed to initialize Discord bot: " + e.getMessage());
            return false;
        }
    }

    private void registerCommands() {
        MineKeo2FACommand mainCmd = new MineKeo2FACommand(this);
        getCommand("minekeo2fa").setExecutor(mainCmd);
        getCommand("minekeo2fa").setTabCompleter(mainCmd);
        getCommand("baomat").setExecutor(new com.anhnguyen.minekeo.commands.BaomatCommand(this));
    }

    // Cho phép các thành phần refresh sau reload config/lang
    public void refreshRuntime() {
        if (discordBotManager != null && jda != null) {
            discordBotManager.refreshAfterReload();
        }
        // Có thể thêm refresh khác nếu cần (cache, session policy, v.v.)
    }

    private void registerListeners() {
        // Register NLogin hook listener
        getServer().getPluginManager().registerEvents(new NLoginListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerFreezeListener(this), this);
    }

    // Getters
    public static MineKeo2FA getInstance() {
        return instance;
    }

    public JDA getJda() {
        return jda;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CaptchaManager getCaptchaManager() {
        return captchaManager;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public PlayerFreezeManager getFreezeManager() {
        return freezeManager;
    }

    public StaffIPManager getStaffIPManager() {
        return staffIPManager;
    }

    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }
    
    public NLoginAPIManager getNLoginAPIManager() {
        return nLoginAPIManager;
    }
}
