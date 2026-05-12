package group.moniepoint.eventsnestserver.chat.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateConversationRequest {

    private String title;
    private UUID eventId;

    @NotEmpty(message = "At least one participant is required")
    private List<String> participantIds;
}
