package group.moniepoint.eventsnestserver.ratings.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AnswerRequest {

    @NotNull(message = "questionId is required")
    private UUID questionId;

    private String answerText;
    private Integer answerNumber;
}
