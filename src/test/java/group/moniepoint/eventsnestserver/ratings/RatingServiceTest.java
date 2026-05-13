package group.moniepoint.eventsnestserver.ratings;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventConfigNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
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
import group.moniepoint.eventsnestserver.ratings.model.RatingAnswer;
import group.moniepoint.eventsnestserver.ratings.model.RatingForm;
import group.moniepoint.eventsnestserver.ratings.model.RatingQuestion;
import group.moniepoint.eventsnestserver.ratings.model.RatingResponse;
import group.moniepoint.eventsnestserver.ratings.repository.RatingFormRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingQuestionRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingResponseRepository;
import group.moniepoint.eventsnestserver.ratings.service.RatingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RatingService Unit Tests")
class RatingServiceTest {

    @Mock private RatingFormRepository formRepository;
    @Mock private RatingQuestionRepository questionRepository;
    @Mock private RatingResponseRepository responseRepository;
    @Mock private EventRespository eventRepository;
    @Mock private EventConfigRepository configRepository;
    @Mock private EventMembershipRepository membershipRepository;

    private RatingServiceImpl ratingService;

    private final UUID eventId = UUID.randomUUID();
    private final UUID formId  = UUID.randomUUID();
    private User organizer;
    private Events event;
    private EventConfig enabledConfig;

    @BeforeEach
    void setUp() {
        ratingService = new RatingServiceImpl(
                formRepository, questionRepository, responseRepository,
                eventRepository, configRepository, membershipRepository);

        organizer     = User.builder().id("organizer001").firstName("Eve").lastName("Org").build();
        event         = Events.builder().id(eventId).title("Tech Conf").build();
        enabledConfig = EventConfig.builder().event(event).ratingsEnabled(true).build();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void stubOrganizer() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(true);
    }

    private void stubNotOrganizer() {
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, organizer.getId(), EventRole.ORGANIZER)).thenReturn(false);
    }

    private void stubRatingsEnabled() {
        when(configRepository.findByEventId(eventId)).thenReturn(Optional.of(enabledConfig));
    }

    private void stubRatingsDisabled() {
        EventConfig disabled = EventConfig.builder().event(event).ratingsEnabled(false).build();
        when(configRepository.findByEventId(eventId)).thenReturn(Optional.of(disabled));
    }

    private RatingForm buildForm() {
        return RatingForm.builder()
                .id(formId).event(event)
                .title("Post-Event Survey")
                .collectAnonymous(false)
                .sendDelayHours(24)
                .questions(new ArrayList<>())
                .build();
    }

    private CreateRatingFormRequest formRequest() {
        CreateRatingFormRequest req = new CreateRatingFormRequest();
        req.setTitle("Post-Event Survey");
        req.setCollectAnonymous(false);
        req.setSendDelayHours(24);
        return req;
    }

    // ─── createForm() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createForm()")
    class CreateForm {

        @Test
        @DisplayName("Organizer can create a rating form when ratings are enabled")
        void organizerCanCreateForm() {
            stubOrganizer();
            stubRatingsEnabled();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.empty());
            when(formRepository.save(any())).thenReturn(buildForm());

            var response = ratingService.createForm(eventId, formRequest(), organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Rating form created");
            assertThat(response.getData().getTitle()).isEqualTo("Post-Event Survey");
            verify(formRepository).save(any(RatingForm.class));
        }

        @Test
        @DisplayName("Throws EventsNestException when a form already exists for this event")
        void throwsWhenFormAlreadyExists() {
            stubOrganizer();
            stubRatingsEnabled();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.of(buildForm()));

            assertThatThrownBy(() -> ratingService.createForm(eventId, formRequest(), organizer))
                    .isInstanceOf(EventsNestException.class)
                    .hasMessageContaining("already exists");
            verify(formRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when caller is not the organizer")
        void throwsWhenNotOrganizer() {
            stubNotOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> ratingService.createForm(eventId, formRequest(), organizer))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws RatingsNotEnabledException when ratings are disabled in config")
        void throwsWhenRatingsDisabled() {
            stubOrganizer();
            stubRatingsDisabled();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> ratingService.createForm(eventId, formRequest(), organizer))
                    .isInstanceOf(RatingsNotEnabledException.class);
        }

        @Test
        @DisplayName("Throws EventNotFoundException for a non-existent event")
        void throwsWhenEventNotFound() {
            when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.createForm(eventId, formRequest(), organizer))
                    .isInstanceOf(EventNotFoundException.class);
        }

        @Test
        @DisplayName("Throws EventConfigNotFoundException when no event config exists")
        void throwsWhenConfigMissing() {
            stubOrganizer();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(configRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.createForm(eventId, formRequest(), organizer))
                    .isInstanceOf(EventConfigNotFoundException.class);
        }
    }

    // ─── getForm() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getForm()")
    class GetForm {

        @Test
        @DisplayName("Returns the rating form with its response count")
        void returnsFormWithResponseCount() {
            stubRatingsEnabled();
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.of(buildForm()));
            when(responseRepository.countByFormId(formId)).thenReturn(7L);

            RatingFormResponse result = ratingService.getForm(eventId);

            assertThat(result.getTitle()).isEqualTo("Post-Event Survey");
            assertThat(result.getResponseCount()).isEqualTo(7L);
        }

        @Test
        @DisplayName("Throws RatingFormNotFoundException when no form exists for the event")
        void throwsWhenFormNotFound() {
            stubRatingsEnabled();
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.getForm(eventId))
                    .isInstanceOf(RatingFormNotFoundException.class);
        }

        @Test
        @DisplayName("Throws RatingsNotEnabledException when ratings are disabled")
        void throwsWhenRatingsDisabled() {
            stubRatingsDisabled();

            assertThatThrownBy(() -> ratingService.getForm(eventId))
                    .isInstanceOf(RatingsNotEnabledException.class);
        }
    }

    // ─── addQuestion() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addQuestion()")
    class AddQuestion {

        @Test
        @DisplayName("Organizer can add a STAR question to the form")
        void organizerCanAddQuestion() {
            stubOrganizer();
            stubRatingsEnabled();
            RatingForm form = buildForm();
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.of(form));
            when(responseRepository.countByFormId(formId)).thenReturn(0L);
            when(questionRepository.save(any())).thenReturn(RatingQuestion.builder()
                    .id(UUID.randomUUID()).form(form)
                    .questionText("How was it?").questionType(QuestionType.STAR).displayOrder(1).build());

            AddQuestionRequest req = new AddQuestionRequest();
            req.setQuestionText("How was it?");
            req.setQuestionType(QuestionType.STAR);
            req.setDisplayOrder(1);
            req.setRequired(true);

            var response = ratingService.addQuestion(eventId, req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Question added");
            verify(questionRepository).save(any(RatingQuestion.class));
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer adds a question")
        void throwsWhenNotOrganizer() {
            stubNotOrganizer();

            AddQuestionRequest req = new AddQuestionRequest();
            req.setQuestionText("Rate the event");
            req.setQuestionType(QuestionType.TEXT);

            assertThatThrownBy(() -> ratingService.addQuestion(eventId, req, organizer))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Throws RatingFormNotFoundException when no form exists for the event")
        void throwsWhenFormNotFound() {
            stubOrganizer();
            stubRatingsEnabled();
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            AddQuestionRequest req = new AddQuestionRequest();
            req.setQuestionText("Rate the event");
            req.setQuestionType(QuestionType.TEXT);

            assertThatThrownBy(() -> ratingService.addQuestion(eventId, req, organizer))
                    .isInstanceOf(RatingFormNotFoundException.class);
        }
    }

    // ─── deleteQuestion() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteQuestion()")
    class DeleteQuestion {

        @Test
        @DisplayName("Organizer can delete a question that belongs to the event form")
        void organizerCanDeleteQuestion() {
            stubOrganizer();
            stubRatingsEnabled();
            RatingForm form = buildForm();
            UUID questionId = UUID.randomUUID();
            RatingQuestion question = RatingQuestion.builder()
                    .id(questionId).form(form)
                    .questionText("Rate it").questionType(QuestionType.STAR).build();

            when(formRepository.findByEventId(eventId)).thenReturn(Optional.of(form));
            when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

            var response = ratingService.deleteQuestion(eventId, questionId, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Question deleted");
            verify(questionRepository).delete(question);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when question ID does not exist")
        void throwsWhenQuestionNotFound() {
            stubOrganizer();
            stubRatingsEnabled();
            UUID questionId = UUID.randomUUID();
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.of(buildForm()));
            when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.deleteQuestion(eventId, questionId, organizer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ─── submitResponse() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitResponse()")
    class SubmitResponse {

        @Test
        @DisplayName("Authenticated user can submit a response to a non-anonymous form")
        void userCanSubmitResponse() {
            stubRatingsEnabled();
            RatingForm form = buildForm(); // collectAnonymous = false
            when(formRepository.findById(formId)).thenReturn(Optional.of(form));
            when(responseRepository.findByFormIdAndRespondentId(formId, organizer.getId()))
                    .thenReturn(Optional.empty());
            when(responseRepository.save(any())).thenReturn(RatingResponse.builder()
                    .id(UUID.randomUUID()).form(form).respondentId(organizer.getId())
                    .submittedAt(LocalDateTime.now()).answers(new ArrayList<>()).build());

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());

            var response = ratingService.submitResponse(formId, req, organizer);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("thank you");
            verify(responseRepository).save(any(RatingResponse.class));
        }

        @Test
        @DisplayName("Throws RatingAlreadySubmittedException when user submits to the same form twice")
        void throwsOnDuplicateNonAnonymousSubmission() {
            stubRatingsEnabled();
            RatingForm form = buildForm(); // collectAnonymous = false
            when(formRepository.findById(formId)).thenReturn(Optional.of(form));
            when(responseRepository.findByFormIdAndRespondentId(formId, organizer.getId()))
                    .thenReturn(Optional.of(RatingResponse.builder().build()));

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());

            assertThatThrownBy(() -> ratingService.submitResponse(formId, req, organizer))
                    .isInstanceOf(RatingAlreadySubmittedException.class);
        }

        @Test
        @DisplayName("Anonymous form skips duplicate check and allows repeat submissions")
        void anonymousFormSkipsDuplicateCheck() {
            RatingForm anonForm = RatingForm.builder()
                    .id(formId).event(event).title("Anon Survey")
                    .collectAnonymous(true).questions(new ArrayList<>()).build();
            when(configRepository.findByEventId(eventId)).thenReturn(Optional.of(enabledConfig));
            when(formRepository.findById(formId)).thenReturn(Optional.of(anonForm));
            when(responseRepository.save(any())).thenReturn(RatingResponse.builder()
                    .id(UUID.randomUUID()).form(anonForm).respondentId(null)
                    .submittedAt(LocalDateTime.now()).answers(new ArrayList<>()).build());

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());

            var response = ratingService.submitResponse(formId, req, organizer);

            assertThat(response.isSuccess()).isTrue();
            verify(responseRepository, never()).findByFormIdAndRespondentId(any(), any());
        }

        @Test
        @DisplayName("Throws RatingFormNotFoundException when formId does not exist")
        void throwsWhenFormNotFound() {
            when(formRepository.findById(formId)).thenReturn(Optional.empty());

            SubmitRatingRequest req = new SubmitRatingRequest();
            req.setAnswers(List.of());

            assertThatThrownBy(() -> ratingService.submitResponse(formId, req, organizer))
                    .isInstanceOf(RatingFormNotFoundException.class);
        }
    }

    // ─── getResponses() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getResponses()")
    class GetResponses {

        @Test
        @DisplayName("Organizer can view all responses for their event")
        void organizerCanViewResponses() {
            stubOrganizer();
            stubRatingsEnabled();
            RatingForm form = buildForm();
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.of(form));
            when(responseRepository.findAllByFormId(formId)).thenReturn(List.of(
                    RatingResponse.builder().id(UUID.randomUUID()).form(form)
                            .respondentId(organizer.getId()).submittedAt(LocalDateTime.now())
                            .answers(new ArrayList<>()).build()));

            List<RatingResponseView> views = ratingService.getResponses(eventId, organizer);

            assertThat(views).hasSize(1);
        }

        @Test
        @DisplayName("Throws NotEventOrganizerException when non-organizer requests responses")
        void throwsWhenNotOrganizer() {
            stubNotOrganizer();

            assertThatThrownBy(() -> ratingService.getResponses(eventId, organizer))
                    .isInstanceOf(NotEventOrganizerException.class);
        }

        @Test
        @DisplayName("Returns empty list when no responses have been submitted yet")
        void returnsEmptyWhenNoResponses() {
            stubOrganizer();
            stubRatingsEnabled();
            when(formRepository.findByEventId(eventId)).thenReturn(Optional.of(buildForm()));
            when(responseRepository.findAllByFormId(formId)).thenReturn(List.of());

            List<RatingResponseView> views = ratingService.getResponses(eventId, organizer);

            assertThat(views).isEmpty();
        }
    }
}
