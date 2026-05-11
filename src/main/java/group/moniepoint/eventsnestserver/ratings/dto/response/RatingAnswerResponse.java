package group.moniepoint.eventsnestserver.ratings.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingAnswerResponse {
    private UUID questionId;
    private String questionText;
    private String answerText;
    private Integer answerNumber;
}
