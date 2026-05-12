package group.moniepoint.eventsnestserver.ratings.dto.response;

import group.moniepoint.eventsnestserver.ratings.model.QuestionType;
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
public class RatingQuestionResponse {
    private UUID id;
    private String questionText;
    private QuestionType questionType;
    private int displayOrder;
    private boolean required;
    private String options;
}
