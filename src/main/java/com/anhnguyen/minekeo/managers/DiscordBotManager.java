package com.anhnguyen.minekeo.managers;

import com.anhnguyen.minekeo.MineKeo2FA;
import com.anhnguyen.minekeo.utils.ConfigManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.dv8tion.jda.api.EmbedBuilder;
import com.anhnguyen.minekeo.utils.LogManager;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Simple cache entry for IP info
class IpInfoCacheEntry {
    final String isp;
    final String location;
    final long expireAt;
    IpInfoCacheEntry(String isp, String location, long ttlMillis) {
        this.isp = isp;
        this.location = location;
        this.expireAt = System.currentTimeMillis() + ttlMillis;
    }
    boolean isExpired() { return System.currentTimeMillis() > expireAt; }
}

public class DiscordBotManager extends ListenerAdapter {
    
    private final MineKeo2FA plugin;
    private final ConfigManager config;
    private JDA jda;
    private final Map<String, VerificationData> pendingVerifications;
    private final Map<String, UnlinkData> pendingUnlinks;
    private final Map<String, IpInfoCacheEntry> ipInfoCache = new ConcurrentHashMap<>();
    private String cachedGuildName;
    private String cachedChannelName;
    
    public DiscordBotManager(MineKeo2FA plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.pendingVerifications = new ConcurrentHashMap<>();
        this.pendingUnlinks = new ConcurrentHashMap<>();
    }
    
    public void initialize(JDA jda) {
        this.jda = jda;
        jda.addEventListener(this);
        
        // Cache và đăng ký lệnh
        refreshAfterReload();
    }
        
    public void refreshAfterReload() {
        // Cache guild/channel
        cacheGuildAndChannelInfo();
        // Register slash commands cho guild cấu hình hiện tại
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || guildId.equals("YOUR_GUILD_ID_HERE")) {
            LogManager.warning("Guild ID not configured! Slash commands not registered.");
            return;
        }
            Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            LogManager.warning("Could not find guild with ID: " + guildId + ". Slash commands not registered.");
            return;
        }
                guild.updateCommands().addCommands(
                    Commands.slash("link", "Liên kết tài khoản Minecraft với Discord")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "captcha", "Mã captcha từ Minecraft", true),
                    Commands.slash("unlink", "Hủy liên kết tài khoản"),
                    Commands.slash("check", "Kiểm tra tài khoản đã liên kết"),
                    Commands.slash("check-admin", "Kiểm tra tài khoản (Admin)")
                        .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.ADMINISTRATOR)),
                    Commands.slash("doimatkhau", "Đổi mật khẩu Minecraft")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "recovery_code", "Mã khôi phục", true)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "new_password", "Mật khẩu mới", true),
                    Commands.slash("link-admin", "Liên kết tài khoản (Admin)")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.USER, "user", "Tag user Discord", true)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "minecraft_name", "Tên tài khoản Minecraft", true)
                        .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.ADMINISTRATOR)),
                    Commands.slash("unlink-admin", "Hủy liên kết tài khoản (Admin)")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "minecraft_name", "Tên tài khoản Minecraft", true)
                        .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.ADMINISTRATOR))
        ).queue(success -> LogManager.info("Slash commands registered successfully for guild: " + guild.getName()),
                 error -> LogManager.severe("Failed to register slash commands: " + error.getMessage()));
    }
    
    private void cacheGuildAndChannelInfo() {
        String guildId = plugin.getConfig().getString("discord.guild-id");
        String channelId = plugin.getConfig().getString("discord.verification-channel-id");
        
        if (guildId != null && !guildId.equals("YOUR_GUILD_ID_HERE")) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                this.cachedGuildName = guild.getName();
            }
        }
        
        if (channelId != null && !channelId.equals("YOUR_VERIFICATION_CHANNEL_ID_HERE")) {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                this.cachedChannelName = channel.getName();
            }
        }
    }
    
    public String getCachedGuildName() {
        return cachedGuildName;
    }
    
    public String getCachedChannelName() {
        return cachedChannelName;
    }
    
    public void sendVerificationRequest(Player player) {
        if (jda == null) {
            LogManager.warning("JDA not initialized, cannot send verification for " + player.getName());
            return;
        }
        String discordId = plugin.getLinkManager().getDiscordId(player.getUniqueId());
        LogManager.info("Sending verification request for player " + player.getName() + ", Discord ID: " + discordId);
        if (discordId == null) {
            LogManager.warning("No Discord ID found for player " + player.getName());
            return;
        }
        jda.retrieveUserById(discordId).queue(user -> {
            String guildId = plugin.getConfig().getString("discord.guild-id");
            if (guildId == null || guildId.equals("YOUR_GUILD_ID_HERE")) {
                LogManager.warning("Guild ID not configured! Cannot send verification.");
                return;
            }
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                LogManager.warning("Could not find guild with ID: " + guildId);
                return;
            }
            guild.retrieveMemberById(user.getId()).queue(member -> {
                String verificationId = UUID.randomUUID().toString();
                VerificationData data = new VerificationData(player.getUniqueId(), player.getName(), verificationId);
                pendingVerifications.put(verificationId, data);
                String ip = player.getAddress().getAddress().getHostAddress();
                String isp = getISPFromIP(ip);
                String location = getLocationFromIP(ip);
                String avatarUrl = "https://minotar.net/avatar/" + player.getName() + "/128.png";
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(config.getDiscordMessage("verify-title"))
                    .setDescription(config.getDiscordMessage("verify-desc"))
                    .addField(config.getDiscordMessage("verify-field-player"), player.getName(), false)
                    .addField(config.getDiscordMessage("verify-field-ip"), ip + " / " + isp, false)
                    .addField(config.getDiscordMessage("verify-field-location"), location, false)
                    .setThumbnail(avatarUrl);
                Button acceptButton = Button.success("verify_accept_" + verificationId, config.getDiscordMessage("verify-btn-accept"));
                Button rejectButton = Button.danger("verify_reject_" + verificationId, config.getDiscordMessage("verify-btn-reject"));
                Button usernameButton = Button.secondary("verify_username_" + verificationId, config.getDiscordMessage("verify-btn-username", "{player}", player.getName())).asDisabled();
                MessageCreateBuilder messageBuilder = new MessageCreateBuilder()
                    .setEmbeds(embed.build())
                    .addActionRow(acceptButton, rejectButton, usernameButton);
                user.openPrivateChannel().queue(channel -> {
                    channel.sendMessage(messageBuilder.build()).queue(
                        message -> {
                            LogManager.info("Verification message sent successfully to DM for " + player.getName());
                            data.setMessageId(message.getId()); // Lưu lại messageId
                        },
                        error -> LogManager.severe("Failed to send verification message to DM for " + player.getName() + ": " + error.getMessage())
                    );
                }, error -> LogManager.severe("Failed to open private channel for " + player.getName() + ": " + error.getMessage()));
            }, error -> {
                LogManager.warning("User " + user.getName() + " is not in the guild (fetch failed)!");
            });
        }, error -> LogManager.warning("Could not fetch Discord user with ID: " + discordId));
    }
    
    // Tra cứu ISP/Location với cache + timeout dựa vào cấu hình ip-lookup
    private String getISPFromIP(String ip) {
        IpInfoCacheEntry cached = ipInfoCache.get(ip);
        if (cached != null && !cached.isExpired()) {
            return cached.isp;
        }
        IpInfoCacheEntry fetched = fetchIpInfo(ip);
        return fetched != null ? fetched.isp : "Unknown ISP";
    }
    
    private String getLocationFromIP(String ip) {
        IpInfoCacheEntry cached = ipInfoCache.get(ip);
        if (cached != null && !cached.isExpired()) {
            return cached.location;
        }
        IpInfoCacheEntry fetched = fetchIpInfo(ip);
        return fetched != null ? fetched.location : "Unknown Location";
    }

    private IpInfoCacheEntry fetchIpInfo(String ip) {
        if (!plugin.getConfig().getBoolean("ip-lookup.enabled", true)) {
            return null;
        }
        // cache hit check again
        IpInfoCacheEntry cached = ipInfoCache.get(ip);
        if (cached != null && !cached.isExpired()) return cached;

        try {
            String provider = plugin.getConfig().getString("ip-lookup.provider", "ipapi");
            int timeoutMs = plugin.getConfig().getInt("ip-lookup.timeout-ms", 2000);
            int ttlSec = plugin.getConfig().getInt("ip-lookup.cache-ttl-seconds", 3600);

            String url = provider.equalsIgnoreCase("ipinfo")
                ? ("https://ipinfo.io/" + ip + "/json")
                : ("https://ipapi.co/" + ip + "/json/");

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MineKeo2FA/1.0");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            try (java.io.InputStream is = conn.getInputStream();
                 java.util.Scanner s = new java.util.Scanner(is, java.nio.charset.StandardCharsets.UTF_8).useDelimiter("\\A")) {
                String body = s.hasNext() ? s.next() : "{}";
                // parse minimal JSON without external lib
                String isp = extractJsonField(body, provider.equalsIgnoreCase("ipinfo") ? "org" : "org");
                if (isp == null || isp.isEmpty()) isp = extractJsonField(body, "isp");
                String city = extractJsonField(body, "city");
                String country = extractJsonField(body, provider.equalsIgnoreCase("ipinfo") ? "country" : "country_name");
                String location = (city != null && !city.isEmpty() ? city : "") + (country != null && !country.isEmpty() ? (city != null && !city.isEmpty() ? " / " : "") + country : "");
                if ((isp == null || isp.isEmpty()) && (location == null || location.isEmpty())) {
                    return null;
                }
                IpInfoCacheEntry entry = new IpInfoCacheEntry(
                    isp != null ? isp : "Unknown ISP",
                    location != null && !location.isEmpty() ? location : "Unknown Location",
                    ttlSec * 1000L
                );
                ipInfoCache.put(ip, entry);
                return entry;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractJsonField(String json, String field) {
        // Rất đơn giản: tìm "field":"value" hoặc "field":value
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(":", idx + key.length());
        if (colon < 0) return null;
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }
    
    public void sendRecoveryCode(String discordId, String playerName, String recoveryCode) {
        jda.retrieveUserById(discordId).queue(user -> {
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(config.getDiscordMessage("recovery-title"))
                .setDescription(config.getDiscordMessage("recovery-desc", "{player}", playerName))
                .addField(config.getDiscordMessage("recovery-field-code"), "`" + recoveryCode + "`", false)
                .addField(config.getDiscordMessage("recovery-field-note"), config.getDiscordMessage("recovery-field-note-value"), false)
                .setColor(Color.GREEN)
                .setFooter(config.getDiscordMessage("recovery-footer"));
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessageEmbeds(embed.build()).queue(
                    success -> LogManager.info("Recovery code sent successfully to DM for " + playerName),
                    error -> LogManager.severe("Failed to send recovery code to DM for " + playerName + ": " + error.getMessage())
                );
            }, error -> LogManager.severe("Failed to open private channel for " + playerName + ": " + error.getMessage()));
        }, error -> LogManager.warning("Could not fetch Discord user with ID: " + discordId));
    }
    
    public void sendUnlinkConfirmation(String discordId, String playerName, String unlinkId) {
        jda.retrieveUserById(discordId).queue(user -> {
            String avatarUrl = "https://minotar.net/armor/body/" + playerName + "/100.png";
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(config.getDiscordMessage("unlink-confirm-title"))
                .setDescription(config.getDiscordMessage("unlink-confirm-desc", "{player}", playerName))
                .addField(config.getDiscordMessage("unlink-confirm-field-warning"), config.getDiscordMessage("unlink-confirm-field-warning-value"), false)
                .setColor(Color.ORANGE)
                .setFooter(config.getDiscordMessage("unlink-confirm-footer"))
                .setTimestamp(java.time.Instant.now())
                .setThumbnail(avatarUrl);
            Button confirmButton = Button.danger("unlink_confirm_" + unlinkId, config.getDiscordMessage("unlink-confirm-btn-confirm"));
            Button cancelButton = Button.secondary("unlink_cancel_" + unlinkId, config.getDiscordMessage("unlink-confirm-btn-cancel"));
            MessageCreateBuilder messageBuilder = new MessageCreateBuilder()
                .setEmbeds(embed.build())
                .addActionRow(confirmButton, cancelButton);
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(messageBuilder.build()).queue(
                    success -> LogManager.info("Unlink confirmation sent successfully to DM for " + playerName),
                    error -> LogManager.severe("Failed to send unlink confirmation to DM for " + playerName + ": " + error.getMessage())
                );
            }, error -> LogManager.severe("Failed to open private channel for " + playerName + ": " + error.getMessage()));
        }, error -> LogManager.warning("Could not fetch Discord user with ID: " + discordId));
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        
        switch (command) {
            case "link":
                handleLinkCommand(event);
                break;
            case "unlink":
                handleUnlinkCommand(event);
                break;
            case "check":
                handleCheckCommand(event);
                break;
            case "check-admin":
                handleCheckAdminCommand(event);
                break;
            case "doimatkhau":
                handleChangePasswordCommand(event);
                break;
            case "link-admin":
                handleLinkAdminCommand(event);
                break;
            case "unlink-admin":
                handleUnlinkAdminCommand(event);
                break;
        }
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        if (buttonId.startsWith("verify_accept_") || buttonId.startsWith("verify_reject_")) {
            String verificationId = buttonId.startsWith("verify_accept_") ? buttonId.substring("verify_accept_".length()) : buttonId.substring("verify_reject_".length());
            VerificationData data = pendingVerifications.get(verificationId);
            if (data != null && data.getMessageId() != null) {
                // Xóa message cũ
                event.getChannel().deleteMessageById(data.getMessageId()).queue(null, ex -> {});
                handleVerificationResponse(verificationId, buttonId.startsWith("verify_accept_"));
                event.reply(config.getDiscordMessage(buttonId.startsWith("verify_accept_") ? "verify-response-accept" : "verify-response-reject")).setEphemeral(true).queue();
            } else {
                event.reply(config.getDiscordMessage("verify-request-expired")).setEphemeral(true).queue();
            }
        } else if (buttonId.startsWith("verify_username_")) {
            // Username button is disabled, just acknowledge
            event.reply(config.getDiscordMessage("verify-response-username")).setEphemeral(true).queue();
        } else if (buttonId.startsWith("unlink_confirm_")) {
            String unlinkId = buttonId.substring("unlink_confirm_".length());
            UnlinkData data = pendingUnlinks.get(unlinkId);
            if (data != null) {
                handleUnlinkConfirmation(unlinkId, true);
                event.reply(config.getDiscordMessage("unlink-confirm-response-accept")).setEphemeral(true).queue();
            } else {
                event.reply(config.getDiscordMessage("unlink-request-expired")).setEphemeral(true).queue();
            }
        } else if (buttonId.startsWith("unlink_cancel_")) {
            String unlinkId = buttonId.substring("unlink_cancel_".length());
            UnlinkData data = pendingUnlinks.get(unlinkId);
            if (data != null) {
                handleUnlinkConfirmation(unlinkId, false);
                event.reply(config.getDiscordMessage("unlink-confirm-response-cancel")).setEphemeral(true).queue();
            } else {
                event.reply(config.getDiscordMessage("unlink-request-expired")).setEphemeral(true).queue();
            }
        }
    }
    
    @Override
    public void onStringSelectInteraction(net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent event) {
        String selectId = event.getComponentId();
        
        if (selectId.startsWith("unlink_select_")) {
            String unlinkId = selectId.substring("unlink_select_".length());
            String selectedValue = event.getValues().get(0); // Lấy giá trị được chọn
            
            try {
                java.util.UUID playerUUID = java.util.UUID.fromString(selectedValue);
                String playerName = getPlayerNameFromUUID(playerUUID);
                String discordId = event.getUser().getId();
                
                // Lưu thông tin unlink
                pendingUnlinks.put(unlinkId, new UnlinkData(playerUUID, playerName, unlinkId));
                
                // Gửi xác nhận hủy liên kết qua DM
                sendUnlinkConfirmation(discordId, playerName, unlinkId);
                
                event.reply(config.getDiscordMessage("unlink-confirm-response-select")).setEphemeral(true).queue();
                
            } catch (IllegalArgumentException e) {
                event.reply(config.getDiscordMessage("unlink-confirm-response-error")).setEphemeral(true).queue();
            }
        }
    }
    
    private void handleLinkCommand(SlashCommandInteractionEvent event) {
        // Kiểm tra xem user có trong guild không
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply(config.getDiscordMessage("link-error-guild")).setEphemeral(true).queue();
            return;
        }
        
        String captcha = event.getOption("captcha").getAsString();
        
        // Verify captcha
        if (!plugin.getCaptchaManager().isCaptchaValid(captcha)) {
            event.reply(config.getDiscordMessage("link-error-captcha")).setEphemeral(true).queue();
            return;
        }
        
        // Get player from captcha
        CaptchaManager.CaptchaData captchaData = plugin.getCaptchaManager().getCaptchaData(captcha);
        if (captchaData == null) {
            event.reply(config.getDiscordMessage("link-error-captcha-data")).setEphemeral(true).queue();
            return;
        }
        
        // Link account
        Player player = Bukkit.getPlayer(captchaData.getPlayerUUID());
        String playerName = player != null ? player.getName() : "Unknown";
        
        // Kiểm tra kết quả link account
        boolean linkSuccess = plugin.getLinkManager().linkAccount(captchaData.getPlayerUUID(), event.getUser().getId(), playerName);
        
        if (!linkSuccess) {
            // Kiểm tra lý do thất bại
            List<UUID> linkedAccounts = plugin.getLinkManager().getLinkedAccounts(event.getUser().getId());
            if (linkedAccounts.size() >= plugin.getConfig().getInt("max-links-per-discord", 3)) {
                event.reply(config.getDiscordMessage("link-error-max-links")).setEphemeral(true).queue();
            } else {
                event.reply(config.getDiscordMessage("link-error-existing-account")).setEphemeral(true).queue();
            }
            return;
        }
        
        // Enable 2FA automatically
        plugin.getLinkManager().setEnabled(captchaData.getPlayerUUID(), true);
        
        // Remove used captcha
        plugin.getCaptchaManager().removeCaptcha(captcha);
        
        // Send success message
        event.reply(config.getDiscordMessage("link-success")).setEphemeral(true).queue();
        
        // Notify player in game (đưa toàn bộ text vào lang.yml)
        if (player != null && player.isOnline()) {
            player.sendMessage(config.getMessage("link-success"));
            player.sendMessage(config.getMessage("auto-2fa-enabled"));
        }
    }
    
    private void handleUnlinkCommand(SlashCommandInteractionEvent event) {
        // Kiểm tra xem user có trong guild không
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply(config.getDiscordMessage("unlink-error-guild")).setEphemeral(true).queue();
            return;
        }
        
        String discordId = event.getUser().getId();
        java.util.List<java.util.UUID> linkedAccounts = plugin.getLinkManager().getLinkedAccounts(discordId);
        
        if (linkedAccounts.isEmpty()) {
            event.reply(config.getDiscordMessage("unlink-error-no-accounts")).setEphemeral(true).queue();
            return;
        }
        
        // Tạo unlink ID
        String unlinkId = UUID.randomUUID().toString();
        
        // Lưu thông tin unlink
        if (linkedAccounts.size() == 1) {
            // Chỉ có 1 tài khoản, hủy liên kết trực tiếp
            java.util.UUID playerUUID = linkedAccounts.get(0);
            String playerName = getPlayerNameFromUUID(playerUUID);
            
            pendingUnlinks.put(unlinkId, new UnlinkData(playerUUID, playerName, unlinkId));
            sendUnlinkConfirmation(discordId, playerName, unlinkId);
            
            event.reply(config.getDiscordMessage("unlink-confirm-single")).setEphemeral(true).queue();
        } else {
            // Có nhiều tài khoản, hiển thị selection menu
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(config.getDiscordMessage("unlink-select-title"))
                .setDescription(config.getDiscordMessage("unlink-select-desc"))
                .setColor(Color.ORANGE)
                .setFooter(config.getDiscordMessage("unlink-select-footer"))
                .setTimestamp(java.time.Instant.now());
            
            // Tạo StringSelectMenu với các option
            net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu.Builder menuBuilder = 
                net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu.create("unlink_select_" + unlinkId)
                .setPlaceholder(config.getDiscordMessage("unlink-select-placeholder"))
                .setRequiredRange(1, 1);
            
            for (int i = 0; i < linkedAccounts.size(); i++) {
                java.util.UUID playerUUID = linkedAccounts.get(i);
                String playerName = getPlayerNameFromUUID(playerUUID);
                String optionValue = playerUUID.toString();
                String optionLabel = playerName + " (" + playerUUID.toString().substring(0, 8) + "...)";
                
                menuBuilder.addOption(optionLabel, optionValue, 
                    config.getDiscordMessage("unlink-select-option", "{player}", playerName));
            }
            
            MessageCreateBuilder messageBuilder = new MessageCreateBuilder()
                .setEmbeds(embed.build())
                .addActionRow(menuBuilder.build());
            
            event.reply(messageBuilder.build()).setEphemeral(true).queue();
        }
    }
    
    private void handleCheckCommand(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply(config.getDiscordMessage("check-error-guild")).setEphemeral(true).queue();
            return;
        }
        java.util.List<java.util.UUID> linkedAccounts = plugin.getLinkManager().getLinkedAccounts(discordId);
        if (linkedAccounts.isEmpty()) {
            event.reply(config.getDiscordMessage("check-error-no-accounts")).setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(config.getDiscordMessage("check-title"))
            .setDescription(config.getDiscordMessage("check-desc"))
            .setColor(Color.BLUE)
            .setFooter(config.getDiscordMessage("check-footer"))
            .setTimestamp(java.time.Instant.now());
        for (java.util.UUID playerUUID : linkedAccounts) {
            String playerName = getPlayerNameFromUUID(playerUUID);
            String discordName = event.getUser().getAsTag();
            embed.addField(playerName, discordName, false);
        }
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    private void handleCheckAdminCommand(SlashCommandInteractionEvent event) {
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply(config.getDiscordMessage("check-admin-error-guild")).setEphemeral(true).queue();
            return;
        }
        String adminRoleId = plugin.getConfig().getString("discord.admin-role-id");
        if (adminRoleId == null || adminRoleId.equals("YOUR_ADMIN_ROLE_ID_HERE")) {
            event.reply(config.getDiscordMessage("check-admin-error-role")).setEphemeral(true).queue();
            return;
        }
        if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(adminRoleId))) {
            event.reply(config.getDiscordMessage("check-admin-error-permission")).setEphemeral(true).queue();
            return;
        }
        java.util.List<java.util.UUID> allLinkedAccounts = plugin.getLinkManager().getAllLinkedAccounts();
        if (allLinkedAccounts.isEmpty()) {
            event.reply(config.getDiscordMessage("check-admin-error-no-accounts")).setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(config.getDiscordMessage("check-admin-title"))
            .setDescription(config.getDiscordMessage("check-admin-desc"))
            .setColor(Color.YELLOW)
            .setFooter(config.getDiscordMessage("check-admin-footer"))
            .setTimestamp(java.time.Instant.now());
        int count = 0;
        for (java.util.UUID playerUUID : allLinkedAccounts) {
            if (count >= 25) {
                embed.addField("...", config.getDiscordMessage("check-admin-more-accounts", "count", String.valueOf(allLinkedAccounts.size() - 25)), false);
                break;
            }
            String playerName = getPlayerNameFromUUID(playerUUID);
            String discordId = plugin.getLinkManager().getDiscordId(playerUUID);
            String discordName = "Unknown";
            if (discordId != null) {
                User discordUser = jda.getUserById(discordId);
                if (discordUser != null) {
                    discordName = discordUser.getAsTag();
                }
            }
            embed.addField(playerName, discordName, false);
            count++;
        }
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    private void handleChangePasswordCommand(SlashCommandInteractionEvent event) {
        // Kiểm tra xem user có trong guild không
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply(config.getDiscordMessage("change-password-error-guild")).setEphemeral(true).queue();
            return;
        }
        
        String recoveryCode = event.getOption("recovery_code") != null ? event.getOption("recovery_code").getAsString() : null;
        String newPassword = event.getOption("new_password") != null ? event.getOption("new_password").getAsString() : null;
        
        if (recoveryCode == null || newPassword == null) {
            event.reply(config.getDiscordMessage("change-password-error-missing")).setEphemeral(true).queue();
            return;
        }
        
        // Find player by Discord ID
        String discordId = event.getUser().getId();
        java.util.List<java.util.UUID> linkedAccounts = plugin.getLinkManager().getLinkedAccounts(discordId);
        
        if (linkedAccounts.isEmpty()) {
            event.reply(config.getDiscordMessage("change-password-error-no-accounts")).setEphemeral(true).queue();
            return;
        }
        
        // For now, use the first linked account
        java.util.UUID playerUUID = linkedAccounts.get(0);
        
        // Validate recovery code
        if (!plugin.getLinkManager().validateRecoveryCode(playerUUID, recoveryCode)) {
            event.reply(config.getDiscordMessage("change-password-error-code")).setEphemeral(true).queue();
            return;
        }
        
        // Change password using NLogin API
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) {
            event.reply(config.getDiscordMessage("change-password-error-online")).setEphemeral(true).queue();
            return;
        }
        
        boolean success = plugin.getNLoginAPIManager().changePassword(player, newPassword);
        
        if (success) {
            // Update recovery code
            plugin.getLinkManager().updateRecoveryCode(playerUUID);
            
            event.reply(config.getDiscordMessage("change-password-success")).setEphemeral(true).queue();
        } else {
            event.reply(config.getDiscordMessage("change-password-error-change")).setEphemeral(true).queue();
        }
    }
    
    private void handleLinkAdminCommand(SlashCommandInteractionEvent event) {
        // Kiểm tra xem user có trong guild không
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply(config.getDiscordMessage("link-admin-error-guild")).setEphemeral(true).queue();
            return;
        }
        
        // Kiểm tra quyền admin
        String adminRoleId = plugin.getConfig().getString("discord.admin-role-id");
        if (adminRoleId == null || adminRoleId.equals("YOUR_ADMIN_ROLE_ID_HERE")) {
            event.reply(config.getDiscordMessage("link-admin-error-role")).setEphemeral(true).queue();
            return;
        }
        
        if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(adminRoleId))) {
            event.reply(config.getDiscordMessage("link-admin-error-permission")).setEphemeral(true).queue();
            return;
        }
        
        // Lấy user Discord được tag và tên Minecraft
        User discordUser = event.getOption("user").getAsUser();
        String discordId = discordUser.getId();
        String minecraftName = event.getOption("minecraft_name").getAsString();
        
        // Tìm UUID của player
        UUID playerUUID = getPlayerUUID(minecraftName);
        if (playerUUID == null) {
            event.reply(config.getDiscordMessage("link-admin-error-player", "{player}", minecraftName)).setEphemeral(true).queue();
            return;
        }
        
        // Kiểm tra xem player đã được link chưa
        if (plugin.getLinkManager().isLinked(playerUUID)) {
            event.reply(config.getDiscordMessage("link-admin-error-linked", "{player}", minecraftName)).setEphemeral(true).queue();
            return;
        }
        
        // Kiểm tra xem Discord ID đã được sử dụng chưa
        List<UUID> linkedAccounts = plugin.getLinkManager().getLinkedAccounts(discordId);
        int maxLinks = plugin.getConfig().getInt("max-links-per-discord", 3);
        if (linkedAccounts.size() >= maxLinks) {
            event.reply(config.getDiscordMessage("link-error-max-links")).setEphemeral(true).queue();
            return;
        }
        
        // Thực hiện link
        boolean success = plugin.getLinkManager().linkAccount(playerUUID, discordId, minecraftName);
        
        if (success) {
            // Enable 2FA tự động
            plugin.getLinkManager().setEnabled(playerUUID, true);
            
            event.reply(config.getDiscordMessage("link-admin-success", "player", minecraftName, "discord_name", discordUser.getAsTag())).setEphemeral(true).queue();
            LogManager.info("Admin " + event.getUser().getName() + " linked player " + minecraftName + " with Discord: " + discordUser.getAsTag());
            
            // Thông báo cho player nếu online
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage("§8[§bMineKeo2FA§8] §aTài khoản của bạn đã được admin liên kết với Discord!");
                player.sendMessage("§8[§bMineKeo2FA§8] §a2FA đã được bật tự động!");
            }
        } else {
            event.reply(config.getDiscordMessage("link-admin-error-link")).setEphemeral(true).queue();
        }
    }
    
    private void handleUnlinkAdminCommand(SlashCommandInteractionEvent event) {
        // Kiểm tra xem user có trong guild không
        String guildId = plugin.getConfig().getString("discord.guild-id");
        if (guildId == null || event.getGuild() == null || !event.getGuild().getId().equals(guildId)) {
            event.reply(config.getDiscordMessage("unlink-admin-error-guild")).setEphemeral(true).queue();
            return;
        }
        
        // Kiểm tra quyền admin
        String adminRoleId = plugin.getConfig().getString("discord.admin-role-id");
        if (adminRoleId == null || adminRoleId.equals("YOUR_ADMIN_ROLE_ID_HERE")) {
            event.reply(config.getDiscordMessage("unlink-admin-error-role")).setEphemeral(true).queue();
            return;
        }
        
        if (!event.getMember().getRoles().contains(event.getGuild().getRoleById(adminRoleId))) {
            event.reply(config.getDiscordMessage("unlink-admin-error-permission")).setEphemeral(true).queue();
            return;
        }
        
        String minecraftName = event.getOption("minecraft_name").getAsString();
        
        // Tìm UUID của player
        UUID playerUUID = null;
        for (UUID uuid : plugin.getLinkManager().getAllLinkedAccounts()) {
            String playerName = plugin.getLinkManager().getPlayerName(uuid);
            if (playerName != null && playerName.equalsIgnoreCase(minecraftName)) {
                playerUUID = uuid;
                break;
            }
        }
        
        if (playerUUID == null) {
            event.reply(config.getDiscordMessage("unlink-admin-error-player", "{player}", minecraftName)).setEphemeral(true).queue();
            return;
        }
        
        String discordId = plugin.getLinkManager().getDiscordId(playerUUID);
        if (discordId == null) {
            event.reply(config.getDiscordMessage("unlink-admin-error-discord", "{player}", minecraftName)).setEphemeral(true).queue();
            return;
        }
        
        // Lấy tên Discord
        String discordName = "Unknown";
        try {
            User discordUser = jda.retrieveUserById(discordId).complete();
            if (discordUser != null) {
                discordName = discordUser.getAsTag();
            }
        } catch (Exception e) {
            LogManager.warning("Could not retrieve Discord user: " + e.getMessage());
        }
        
        // Hủy liên kết
        plugin.getLinkManager().unlinkAccount(playerUUID);
        
        // Gửi thông báo
        event.reply(config.getDiscordMessage("unlink-admin-success", "player", minecraftName, "discord_name", discordName)).setEphemeral(true).queue();
        
        // Log
        LogManager.info("Admin unlinked account: " + minecraftName + " from Discord: " + discordName);
        
        // Thông báo cho player nếu online
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.sendMessage("§8[§bMineKeo2FA§8] §cTài khoản của bạn đã được admin hủy liên kết khỏi Discord!");
            player.sendMessage("§8[§bMineKeo2FA§8] §eBạn cần liên kết lại để sử dụng 2FA!");
        }
    }
    
    private UUID getPlayerUUID(String playerName) {
        // Thử tìm player online trước
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            return player.getUniqueId();
        }
        // Nếu không online, thử tìm trong database
        for (UUID uuid : plugin.getLinkManager().getAllLinkedAccounts()) {
            String name = plugin.getLinkManager().getPlayerName(uuid);
            if (name != null && name.equalsIgnoreCase(playerName)) {
                return uuid;
            }
        }
        // Nếu không có trong database, tạo UUID offline từ tên (giống Bukkit)
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    // Helper method to get player name from UUID
    private String getPlayerNameFromUUID(java.util.UUID playerUUID) {
        // Try to get from online player first
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            return player.getName();
        }
        
        // Try to get from LinkManager data
        String playerName = plugin.getLinkManager().getPlayerName(playerUUID);
        if (playerName != null) {
            return playerName;
        }
        
        // Fallback to placeholder
        return "Player_" + playerUUID.toString().substring(0, 8);
    }
    
    public void handleVerificationResponse(String verificationId, boolean accepted) {
        VerificationData data = pendingVerifications.remove(verificationId);
        if (data == null) return;
        Player player = Bukkit.getPlayer(data.getPlayerUUID());
        if (player == null) return;
        if (accepted) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getFreezeManager().unfreezePlayer(player);
                
                // Tạo session và cập nhật IP
                plugin.getSessionManager().createSession(data.getPlayerUUID());
                String currentIP = player.getAddress().getAddress().getHostAddress();
                plugin.getLinkManager().updateLastIP(data.getPlayerUUID(), currentIP);
                
                // Nếu là OP thì gửi thông báo đã mở khóa
                if (player.isOp()) {
                    player.sendMessage(config.getMessage("op-unfreeze-success"));
                } else {
                    player.sendMessage(config.getMessage("unfreeze-success"));
                }
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(config.getMessage("kick-verification-failed")));
        }
    }
    
    public void handleUnlinkConfirmation(String unlinkId, boolean confirmed) {
        UnlinkData data = pendingUnlinks.remove(unlinkId);
        if (data == null) {
            LogManager.warning("[UNLINK] Không tìm thấy dữ liệu unlink cho unlinkId: " + unlinkId);
            // Gửi message lỗi về Discord nếu có thể
            // (Không có event context ở đây, chỉ log được)
            return;
        }
        if (confirmed) {
            // Run on main thread to avoid async issues
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    boolean unlinkSuccess = plugin.getLinkManager().unlinkAccount(data.getPlayerUUID());
                    if (unlinkSuccess) {
                        LogManager.info("[UNLINK] Successfully unlinked account: " + data.getPlayerName() + " (UUID: " + data.getPlayerUUID() + ")");
                        Player player = Bukkit.getPlayer(data.getPlayerUUID());
                        if (player != null && player.isOnline()) {
                            // KHÔNG gọi unfreezePlayer để tránh gửi message xác minh thành công
                            player.sendMessage(config.getMessage("unlink-success"));
                            player.sendMessage(config.getMessage("enable-2fa"));
                        }
                    } else {
                        LogManager.warning("[UNLINK] Failed to unlink account: " + data.getPlayerName() + " (UUID: " + data.getPlayerUUID() + ")");
                        // Gửi message lỗi về Discord nếu có thể (cần truyền thêm event context nếu muốn gửi reply)
                    }
                }
            }.runTask(plugin);
        }
    }

    // Dọn các verification/unlink quá cũ (tránh rò rỉ map tạm)
    public void cleanupStaleRequests(long olderThanMillis) {
        long now = System.currentTimeMillis();
        pendingVerifications.entrySet().removeIf(e -> {
            // Không có timestamp trong dữ liệu; hiện tại không thể xác định tuổi, nên chỉ dựa vào messageId null
            return e.getValue().getMessageId() == null; // lightweight cleanup
        });
        pendingUnlinks.entrySet().removeIf(e -> false); // placeholder nếu cần tiêu chí
        // Ghi chú: Có thể mở rộng VerificationData/UnlinkData để lưu createdAt và cleanup chính xác hơn.
    }
    
    public void sendStaffIPAlert(String playerName, String ip) {
        String channelId = plugin.getConfig().getString("discord.staff-alert-channel-id");
        if (channelId == null || channelId.equals("YOUR_STAFF_ALERT_CHANNEL_ID_HERE")) {
            return;
        }
        
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(config.getDiscordMessage("staff-ip-alert-title"))
            .setDescription(config.getDiscordMessage("staff-ip-alert-desc"))
            .addField(config.getDiscordMessage("staff-ip-alert-field-player"), playerName, true)
            .addField(config.getDiscordMessage("staff-ip-alert-field-ip"), ip, true)
            .setColor(Color.RED)
            .setFooter(config.getDiscordMessage("staff-ip-alert-footer"))
            .setTimestamp(java.time.Instant.now());
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    public void sendBlacklistAlert(String playerName, String ip) {
        String channelId = plugin.getConfig().getString("discord.staff-alert-channel-id");
        if (channelId == null || channelId.equals("YOUR_STAFF_ALERT_CHANNEL_ID_HERE")) return;
        if (jda == null) return;
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(config.getDiscordMessage("blacklist-alert-title"))
            .setDescription(config.getDiscordMessage("blacklist-alert-desc", "{player}", playerName))
            .addField(config.getDiscordMessage("blacklist-alert-field-player"), playerName, true)
            .addField(config.getDiscordMessage("blacklist-alert-field-ip"), ip, true)
            .setColor(Color.RED)
            .setTimestamp(java.time.Instant.now())
            .setFooter(config.getDiscordMessage("blacklist-alert-footer"));
        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    public Map<String, UnlinkData> getPendingUnlinks() {
        return pendingUnlinks;
    }
    
    public static class VerificationData {
        private final UUID playerUUID;
        private final String playerName;
        private final String verificationId;
        private String messageId; // Thêm trường này để lưu messageId
        
        public VerificationData(UUID playerUUID, String playerName, String verificationId) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.verificationId = verificationId;
        }
        
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public String getVerificationId() { return verificationId; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
    }
    
    public static class UnlinkData {
        private final UUID playerUUID;
        private final String playerName;
        private final String unlinkId;
        
        public UnlinkData(UUID playerUUID, String playerName, String unlinkId) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.unlinkId = unlinkId;
        }
        
        public UUID getPlayerUUID() {
            return playerUUID;
        }
        
        public String getPlayerName() {
            return playerName;
        }
        
        public String getUnlinkId() {
            return unlinkId;
        }
    }
} 