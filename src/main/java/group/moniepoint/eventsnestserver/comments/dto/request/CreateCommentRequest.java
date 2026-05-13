package group.moniepoint.eventsnestserver.comments.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateCommentRequest {

    @NotBlank(message = "body is required")
    @Size(max = 2000, message = "body must be 2000 characters or fewer")
    private String body;

    /** Optional. When set, this becomes a reply to the given comment. */
    private UUID parentId;
}
