package group.moniepoint.eventsnestserver.events.controllers;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.events.dto.response.EventAnalyticsResponse;
import group.moniepoint.eventsnestserver.events.service.EventAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/analytics")
@AllArgsConstructor
@Tag(name = "Analytics", description = "Per-event organizer analytics dashboard")
public class EventAnalyticsController {

    private final EventAnalyticsService analyticsService;
    private final AuthService authService;

    @Operation(summary = "Get analytics dashboard for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<?> getAnalytics(@PathVariable UUID eventId, Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(analyticsService.getAnalytics(eventId, organizer));
    }

    @Operation(summary = "Export analytics as CSV",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAnalytics(@PathVariable UUID eventId, Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        EventAnalyticsResponse data = analyticsService.getAnalytics(eventId, organizer);

        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Value\n");
        csv.append("Tickets Sold,").append(data.getTicketsSold()).append('\n');
        csv.append("Total Revenue,").append(data.getTotalRevenue()).append('\n');
        csv.append("Check-in Rate,").append(String.format("%.2f%%", data.getCheckInRate() * 100)).append('\n');
        csv.append('\n');

        csv.append("Tier,Sold,Total Capacity\n");
        for (var t : data.getTicketsByTier()) {
            csv.append(t.getTierName()).append(',').append(t.getSold()).append(',').append(t.getTotalCapacity()).append('\n');
        }
        csv.append('\n');

        csv.append("Day Number,Check-ins\n");
        for (var d : data.getCheckInsByDay()) {
            csv.append(d.getDayNumber()).append(',').append(d.getCount()).append('\n');
        }
        csv.append('\n');

        csv.append("Date,Bookings\n");
        for (var b : data.getBookingsByDate()) {
            csv.append(b.getDate()).append(',').append(b.getCount()).append('\n');
        }

        if (data.getRsvpSummary() != null) {
            csv.append('\n');
            csv.append("RSVP Status,Count\n");
            data.getRsvpSummary().forEach((status, count) ->
                    csv.append(status).append(',').append(count).append('\n'));
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
