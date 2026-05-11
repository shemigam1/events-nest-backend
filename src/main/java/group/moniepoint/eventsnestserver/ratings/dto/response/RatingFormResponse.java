package group.moniepoint.eventsnestserver.ratings.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingFormResponse {
    private UUID id;
    private UUID eventId;
    private String title;
    private boolean collectAnonymous;
    private int sendDelayHours;
    private LocalDateTime sentAt;
    private long responseCount;
    private List<RatingQuestionResponse> questions;
    private LocalDateTime createdAt;
}
