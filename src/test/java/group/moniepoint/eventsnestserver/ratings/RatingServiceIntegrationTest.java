package group.moniepoint.eventsnestserver.ratings;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.config.IntegrationTestConfig;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.ratings.RatingAlreadySubmittedException;
import group.moniepoint.eventsnestserver.exception.ratings.RatingFormNotFoundException;
import group.moniepoint.eventsnestserver.exception.ratings.RatingsNotEnabledException;
import group.moniepoint.eventsnestserver.ratings.dto.request.AddQuestionRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.AnswerRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.CreateRatingFormRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.SubmitRatingRequest;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingFormResponse;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingResponseView;
import group.moniepoint.eventsnestserver.ratings.model.QuestionType;
import group.moniepoint.eventsnestserver.ratings.repository.RatingFormRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingQuestionRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingResponseRepository;
import group.moniepoint.eventsnestserver.ratings.service.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for RatingService.
 *
 * Uses H2 in-memory database (JPA create-drop).
 * EventConfig with ratingsEnabled=true is seeded in {@code @BeforeEach}.
 * Redis and WebSocket dependencies are mocked by IntegrationTestConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RatingService Integration Tests")
@Import(IntegrationTestConfig.class)
class RatingServiceIntegrationTest {

    @Autowired private RatingService ratingService;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRespository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private EventConfigRepository configRepository;
    @Autowired private RatingFormRepository formRepository;
    @Autowired private RatingQuestionRepository questionRepository;
    @Autowired private RatingResponseRepository responseRepository;

    private User organizer;
    private User attendee;
    private Events event;

    @BeforeEach
    void seed() {
        organizer = userRepository.save(User.builder()
                .id("organizer001")
                .firstName("Eve")
                .lastName("Organizer")
                .email("eve@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        attendee = userRepository.save(User.builder()
                .id("attendee0001")
                .firstName("Alice")
                .lastName("Attendee")
                .email("alice@test.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .build());

        event = eventRepository.save(Events.builder()
                .title("DevFest 2025")
                .description("Developer festival")
                .venue("Landmark Event Centre")
                .startTime(LocalDateTime.now().plusDays(30))
                .endTime(LocalDateTime.now().plusDays(30).plusHours(8))
                .status(EventStatus.PUBLISHED)
                .createdBy(organizer)
                .build());

        membershipRepository.save(EventMembership.builder()
                .events(event)
                .user(organizer)
                .role(EventRole.ORGANIZER)
                .assignedBy(organizer)
                .build());

        // Enable ratings in the event config
        configRepository.save(EventConfig.builder()
                .event(event)
                .ratingsEnabled(true)
                .build());
    }

    // ─── createForm() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createForm()")
    class CreateForm {

        @Test
        @DisplayName("Organizer can create a rating form when ratings are enabled")
        void organizerCanCreateForm() {
            EventsNestResponse<RatingFormResponse> response =
                    ratingService.createForm(event.getId(), formRequest("Post-Event Survey"), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Rating form created");
            assertThat(response.getData().getTitle()).isEqualTo("Post-Event Survey");
            assertThat(response.getData().getEventId()).isEqualTo(event.getId());
        }

        @Test
        @DisplayName("Throws EventsNestException when a form already exists for the event")
        void throwsWhenFormAlreadyExists() {
            ratingService.createForm(event.getId(), formRequest("First Form"), organizer);

            assertThatThrownBy(() ->
                    ratingService.createForm(event.getId(), formRequest("Second Form"), organizer))
                    .isInstanceOf(EventsNestException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("Throws RatingsNotEnabledException when ratings are disabled in config")
        void throwsWhenRatingsDisabled() {
            configRepository.findByEventId(event.getId()).ifPresent(c -> {
                c.setRatingsEnabled(false);
                configRepository.save(c);
            });

            assertThatThrownBy(() ->
                    ratingService.createForm(event.getId(), formRequest("Survey"), organizer))
                    .isInstanceOf(RatingsNotEnabledException.class);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when a non-organizer tries to create a form")
        void throwsForNonOrganizer() {
            assertThatThrownBy(() ->
                    ratingService.createForm(event.getId(), formRequest("Survey"), attendee))
                    .isInstanceOf(NotEventOrganizerException.class);
        }
    }

    // ─── addQuestion() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addQuestion()")
    class AddQuestion {

        @Test
        @DisplayName("Organizer can add a star-rating question to the form")
        void organizerCanAddQuestion() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);

            AddQuestionRequest req = new AddQuestionRequest();
            req.setQuestionText("How would you rate the event overall?");
            req.setQuestionType(QuestionType.STAR);
            req.setDisplayOrder(1);
            req.setRequired(true);

            EventsNestResponse<RatingFormResponse> response =
                    ratingService.addQuestion(event.getId(), req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData().getQuestions()).hasSize(1);
            assertThat(response.getData().getQuestions().get(0).getQuestionText())
                    .isEqualTo("How would you rate the event overall?");
        }

        @Test
        @DisplayName("Questions are stored in displayOrder ascending")
        void questionsAreOrderedByDisplayOrder() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);

            AddQuestionRequest q3 = questionReq("Third question", QuestionType.TEXT, 3);
            AddQuestionRequest q1 = questionReq("First question", QuestionType.STAR, 1);
            AddQuestionRequest q2 = questionReq("Second question", QuestionType.NPS, 2);

            ratingService.addQuestion(event.getId(), q3, organizer);
            ratingService.addQuestion(event.getId(), q1, organizer);
            ratingService.addQuestion(event.getId(), q2, organizer);

            RatingFormResponse form = ratingService.getForm(event.getId());

            assertThat(form.getQuestions())
                    .extracting(q -> q.getDisplayOrder())
                    .containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("Throws RatingFormNotFoundException when no form exists for the event")
        void throwsWhenFormNotFound() {
            AddQuestionRequest req = questionReq("Question?", QuestionType.TEXT, 1);

            assertThatThrownBy(() -> ratingService.addQuestion(event.getId(), req, organizer))
                    .isInstanceOf(RatingFormNotFoundException.class);
        }
    }

    // ─── deleteQuestion() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteQuestion()")
    class DeleteQuestion {

        @Test
        @DisplayName("Organizer can delete a question from the form")
        void organizerCanDeleteQuestion() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);
            RatingFormResponse formWithQuestion = ratingService.addQuestion(
                    event.getId(), questionReq("Rate it", QuestionType.STAR, 1), organizer).getData();

            UUID questionId = formWithQuestion.getQuestions().get(0).getId();

            ratingService.deleteQuestion(event.getId(), questionId, organizer);

            assertThat(questionRepository.findById(questionId)).isEmpty();
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException for a question that does not belong to the form")
        void throwsForQuestionFromAnotherForm() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);

            assertThatThrownBy(() ->
                    ratingService.deleteQuestion(event.getId(), UUID.randomUUID(), organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── submitResponse() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitResponse()")
    class SubmitResponse {

        @Test
        @DisplayName("Attendee can submit a rating response")
        void attendeeCanSubmitResponse() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);
            UUID formId = formRepository.findByEventId(event.getId()).orElseThrow().getId();

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());

            EventsNestResponse<RatingResponseView> response =
                    ratingService.submitResponse(formId, req, attendee);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("thank you");
            assertThat(responseRepository.countByFormId(formId)).isEqualTo(1);
        }

        @Test
        @DisplayName("Throws RatingAlreadySubmittedException when the same user submits twice")
        void throwsOnDuplicateNonAnonymousSubmission() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);
            UUID formId = formRepository.findByEventId(event.getId()).orElseThrow().getId();

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());
            ratingService.submitResponse(formId, req, attendee);

            assertThatThrownBy(() -> ratingService.submitResponse(formId, req, attendee))
                    .isInstanceOf(RatingAlreadySubmittedException.class);
        }

        @Test
        @DisplayName("Anonymous form allows the same user to submit multiple responses")
        void anonymousFormAllowsMultipleSubmissions() {
            CreateRatingFormRequest anonReq = new CreateRatingFormRequest();
            anonReq.setTitle("Anonymous Survey");
            anonReq.setCollectAnonymous(true);
            ratingService.createForm(event.getId(), anonReq, organizer);
            UUID formId = formRepository.findByEventId(event.getId()).orElseThrow().getId();

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());

            ratingService.submitResponse(formId, req, attendee);
            ratingService.submitResponse(formId, req, attendee);

            assertThat(responseRepository.countByFormId(formId)).isEqualTo(2);
        }
    }

    // ─── getResponses() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getResponses()")
    class GetResponses {

        @Test
        @DisplayName("Organizer can view all submitted responses")
        void organizerCanViewResponses() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);
            UUID formId = formRepository.findByEventId(event.getId()).orElseThrow().getId();

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());
            ratingService.submitResponse(formId, req, attendee);

            List<RatingResponseView> responses = ratingService.getResponses(event.getId(), organizer);

            assertThat(responses).hasSize(1);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when a non-organizer requests responses")
        void throwsForNonOrganizer() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);

            assertThatThrownBy(() -> ratingService.getResponses(event.getId(), attendee))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Returns empty list when no responses have been submitted yet")
        void returnsEmptyWhenNoResponses() {
            ratingService.createForm(event.getId(), formRequest("Survey"), organizer);

            List<RatingResponseView> responses = ratingService.getResponses(event.getId(), organizer);

            assertThat(responses).isEmpty();
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateRatingFormRequest formRequest(String title) {
        CreateRatingFormRequest req = new CreateRatingFormRequest();
        req.setTitle(title);
        req.setCollectAnonymous(false);
        req.setSendDelayHours(24);
        return req;
    }

    private AddQuestionRequest questionReq(String text, QuestionType type, int order) {
        AddQuestionRequest req = new AddQuestionRequest();
        req.setQuestionText(text);
        req.setQuestionType(type);
        req.setDisplayOrder(order);
        req.setRequired(true);
        return req;
    }
}
