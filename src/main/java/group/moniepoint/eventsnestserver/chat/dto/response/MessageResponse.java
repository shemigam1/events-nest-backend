package group.moniepoint.eventsnestserver.chat.dto.response;

import group.moniepoint.eventsnestserver.chat.model.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MessageResponse {

    private UUID id;
    private UUID conversationId;
    private String senderId;
    private String senderName;
    private String content;
    private String messageType;
    private LocalDateTime sentAt;

    public static MessageResponse from(Message m) {
        String name = m.getSender().getFirstName() + " " + m.getSender().getLastName();
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(m.getSender().getId())
                .senderName(name.trim())
                .content(m.getContent())
                .messageType(m.getMessageType().name())
                .sentAt(m.getSentAt())
                .build();
    }
}
