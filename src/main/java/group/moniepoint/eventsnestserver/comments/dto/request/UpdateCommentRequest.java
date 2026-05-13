package group.moniepoint.eventsnestserver.comments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCommentRequest {

    @NotBlank(message = "body is required")
    @Size(max = 2000, message = "body must be 2000 characters or fewer")
    private String body;
}
