package group.moniepoint.eventsnestserver.ratings.controller;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.service.AuthService;
import group.moniepoint.eventsnestserver.ratings.dto.request.AddQuestionRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.CreateRatingFormRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.SubmitRatingRequest;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingResponseView;
import group.moniepoint.eventsnestserver.ratings.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
@Tag(name = "Ratings", description = "Post-event ratings & feedback")
public class RatingController {

    private final RatingService ratingService;
    private final AuthService authService;

    @Operation(summary = "Create a rating form for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/ratings/form")
    public ResponseEntity<?> createForm(@PathVariable UUID eventId,
                                        @Valid @RequestBody CreateRatingFormRequest request,
                                        Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(ratingService.createForm(eventId, request, organizer));
    }

    @Operation(summary = "Get the rating form for an event (public when ratings enabled)")
    @GetMapping("/api/v1/events/{eventId}/ratings/form")
    public ResponseEntity<?> getForm(@PathVariable UUID eventId) {
        return ResponseEntity.ok(ratingService.getForm(eventId));
    }

    @Operation(summary = "Add a question to the rating form",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/events/{eventId}/ratings/form/questions")
    public ResponseEntity<?> addQuestion(@PathVariable UUID eventId,
                                         @Valid @RequestBody AddQuestionRequest request,
                                         Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(ratingService.addQuestion(eventId, request, organizer));
    }

    @Operation(summary = "Delete a question from the rating form",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/v1/events/{eventId}/ratings/form/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(@PathVariable UUID eventId,
                                            @PathVariable UUID questionId,
                                            Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(ratingService.deleteQuestion(eventId, questionId, organizer));
    }

    /**
     * Public endpoint — called from the link in the dispatch email.
     * Auth is optional: if a token is present the respondent is attributed.
     */
    @Operation(summary = "Submit a rating response (public; auth optional for attribution)")
    @PostMapping("/api/v1/ratings/{formId}/respond")
    public ResponseEntity<?> submitResponse(@PathVariable UUID formId,
                                            @Valid @RequestBody SubmitRatingRequest request,
                                            Principal principal) {
        User currentUser = (principal != null) ? authService.findByEmail(principal.getName()) : null;
        return ResponseEntity.ok(ratingService.submitResponse(formId, request, currentUser));
    }

    @Operation(summary = "List all rating responses for an event",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/ratings/responses")
    public ResponseEntity<?> getResponses(@PathVariable UUID eventId, Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        return ResponseEntity.ok(ratingService.getResponses(eventId, organizer));
    }

    @Operation(summary = "Export rating responses as CSV",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/v1/events/{eventId}/ratings/export")
    public ResponseEntity<byte[]> exportRatings(@PathVariable UUID eventId, Principal principal) {
        User organizer = authService.findByEmail(principal.getName());
        List<RatingResponseView> responses = ratingService.getResponses(eventId, organizer);

        StringBuilder csv = new StringBuilder();
        csv.append("Response ID,Respondent ID,Submitted At,Question,Answer Text,Answer Number\n");
        for (RatingResponseView resp : responses) {
            for (var answer : resp.getAnswers()) {
                csv.append(resp.getId()).append(',')
                   .append(resp.getRespondentId() != null ? resp.getRespondentId() : "anonymous").append(',')
                   .append(resp.getSubmittedAt()).append(',')
                   .append(escape(answer.getQuestionText())).append(',')
                   .append(escape(answer.getAnswerText())).append(',')
                   .append(answer.getAnswerNumber() != null ? answer.getAnswerNumber() : "").append('\n');
            }
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ratings.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
