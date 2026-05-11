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
public class RatingResponseView {
    private UUID id;
    private String respondentId;
    private LocalDateTime submittedAt;
    private List<RatingAnswerResponse> answers;
}
