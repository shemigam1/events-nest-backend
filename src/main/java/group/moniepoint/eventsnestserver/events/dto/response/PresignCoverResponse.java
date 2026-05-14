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
 *   <li>{@code uploadUrl}  — short-lived PUT URL; frontend sends raw bytes here.</li>
 *   <li>{@code publicUrl}  — permanent URL persisted on the event; use after upload is complete.</li>
 *   <li>{@code previewUrl} — short-lived GET URL for in-browser preview right after upload.</li>
 *   <li>{@code contentType} — Content-Type the frontend MUST include in its PUT request.</li>
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
    private String previewUrl;
    private String contentType;
}
