package vitbuk.com.Ambotorix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.chat.Platform;

@Component
@ConfigurationProperties(prefix = "bot")
public class BotConfig {

    private String token;
    private String username;
    private Long adminId;
    private String discordAdminId;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getDiscordAdminId() {
        return discordAdminId;
    }

    public void setDiscordAdminId(String discordAdminId) {
        this.discordAdminId = discordAdminId;
    }

    /**
     * The admin's id on a given platform, or null if none is configured there. Admin rights do not
     * carry across platforms — the same person is a different account on each.
     */
    public String adminIdFor(Platform platform) {
        return switch (platform) {
            case TELEGRAM -> adminId == null ? null : String.valueOf(adminId);
            case DISCORD -> discordAdminId;
        };
    }
}