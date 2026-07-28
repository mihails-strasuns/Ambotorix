package vitbuk.com.Ambotorix.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ChatGatewayRegistry;
import vitbuk.com.Ambotorix.chat.OutgoingMessage;
import vitbuk.com.Ambotorix.chat.UserRef;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ChatGatewayRegistry chat;

    @Value("${bot.admin.id}")
    private Long adminTelegramId;

    public NotificationService(ChatGatewayRegistry chat) {
        this.chat = chat;
    }

    public void notifyError(String subject, String body) {
        UserRef admin = UserRef.telegram(adminTelegramId, null);
        boolean delivered = chat.send(OutgoingMessage.to(admin)
                .text("⚠️ Ambotorix error:\n" + subject + "\n" + body)
                .build()).isPresent();
        if (!delivered) {
            // Nowhere left to escalate — at least make sure the alert survives in the log.
            log.error("Could not reach the admin with an alert: {} / {}", subject, body);
        }
    }
}
