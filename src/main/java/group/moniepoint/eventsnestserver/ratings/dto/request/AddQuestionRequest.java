package group.moniepoint.eventsnestserver.ratings.dto.request;

import group.moniepoint.eventsnestserver.ratings.model.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddQuestionRequest {

    @NotBlank(message = "questionText is required")
    private String questionText;

    @NotNull(message = "questionType is required")
    private QuestionType questionType;

    private int displayOrder = 0;
    private boolean required = true;

    /** Comma-separated choices; required when questionType = MULTIPLE_CHOICE. */
    private String options;
}
