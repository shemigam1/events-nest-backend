package group.moniepoint.eventsnestserver.admin.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserSummaryResponse {

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private boolean enabled;
    private boolean vendorVerified;
    private String vendorVerificationStatus;
    private LocalDateTime createdAt;
}
