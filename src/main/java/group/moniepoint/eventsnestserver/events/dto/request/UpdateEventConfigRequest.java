package group.moniepoint.eventsnestserver.events.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PATCH semantics: only fields present in the request are updated.
 * Boxed Boolean (not primitive) so null means "leave unchanged".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventConfigRequest {
    private Boolean ticketingEnabled;
    private Boolean guestListEnabled;
    private Boolean programmeEnabled;
    private Boolean ratingsEnabled;
    private Boolean commentsEnabled;
}
