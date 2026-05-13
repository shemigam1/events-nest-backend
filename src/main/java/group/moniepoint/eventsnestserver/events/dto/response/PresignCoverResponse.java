package group.moniepoint.eventsnestserver.events.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Returned by the presign endpoint.
 *
 * <ul>
 *   <li>{@code uploadUrl}  — short-lived URL the frontend PUTs the raw file bytes to (S3 or local).</li>
 *   <li>{@code publicUrl}  — permanent URL already saved on the event; render this once upload is done.</li>
 *   <li>{@code contentType} — the Content-Type the frontend MUST send in its PUT request.</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignCoverResponse {
    private UUID eventId;
    private String uploadUrl;
    private String publicUrl;
    private String contentType;
}
