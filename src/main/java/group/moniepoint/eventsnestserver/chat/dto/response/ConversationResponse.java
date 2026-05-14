package group.moniepoint.eventsnestserver.chat.dto.response;

import group.moniepoint.eventsnestserver.chat.model.Conversation;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ConversationResponse {

    private UUID id;
    private String title;
    private UUID eventId;
    private String type;
    private List<ParticipantSummary> participants;
    private long unreadCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class ParticipantSummary {
        private String userId;
        private String name;
        private String email;
    }

    public static ConversationResponse from(Conversation c) {
        return from(c, 0L);
    }

    public static ConversationResponse from(Conversation c, long unreadCount) {
        List<ParticipantSummary> parts = c.getParticipants().stream()
                .map(p -> ParticipantSummary.builder()
                        .userId(p.getUser().getId())
                        .name(p.getUser().getFirstName() + " " + p.getUser().getLastName())
                        .email(p.getUser().getEmail())
                        .build())
                .toList();

        return ConversationResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .eventId(c.getEvent() != null ? c.getEvent().getId() : null)
                .type(c.getType().name())
                .participants(parts)
                .unreadCount(unreadCount)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
