package group.moniepoint.eventsnestserver.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {

    private UUID conversationId;

    @NotBlank(message = "Message content cannot be blank")
    private String content;

    private String messageType;
}
