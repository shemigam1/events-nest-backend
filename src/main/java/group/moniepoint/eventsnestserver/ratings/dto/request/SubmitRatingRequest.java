package group.moniepoint.eventsnestserver.ratings.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SubmitRatingRequest {

    @NotEmpty(message = "at least one answer is required")
    @Valid
    private List<AnswerRequest> answers;
}
