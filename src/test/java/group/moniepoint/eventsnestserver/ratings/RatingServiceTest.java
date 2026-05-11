package group.moniepoint.eventsnestserver.ratings;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
import group.moniepoint.eventsnestserver.exception.ratings.RatingAlreadySubmittedException;
import group.moniepoint.eventsnestserver.exception.ratings.RatingFormNotFoundException;
import group.moniepoint.eventsnestserver.exception.ratings.RatingsNotEnabledException;
import group.moniepoint.eventsnestserver.ratings.dto.request.AnswerRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.CreateRatingFormRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.SubmitRatingRequest;
import group.moniepoint.eventsnestserver.ratings.model.RatingForm;
import group.moniepoint.eventsnestserver.ratings.model.RatingQuestion;
import group.moniepoint.eventsnestserver.ratings.model.RatingResponse;
import group.moniepoint.eventsnestserver.ratings.repository.RatingFormRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingQuestionRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingResponseRepository;
import group.moniepoint.eventsnestserver.ratings.service.RatingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingFormRepository formRepository;
    @Mock private RatingQuestionRepository questionRepository;
    @Mock private RatingResponseRepository responseRepository;
    @Mock private EventRespository eventRepository;
    @Mock private EventConfigRepository configRepository;
    @Mock private EventMembershipRepository membershipRepository;

    private RatingServiceImpl ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingServiceImpl(
                formRepository, questionRepository, responseRepository,
                eventRepository, configRepository, membershipRepository);
    }

    private User organizer(UUID eventId) {
        User user = User.builder().id("org-1").build();
        when(membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, "org-1", EventRole.ORGANIZER))
                .thenReturn(true);
        return user;
    }

    private void enableRatings(UUID eventId) {
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().ratingsEnabled(true).build()));
    }

    @Test
    void createForm_savesFormSuccessfully() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).title("Conf").build();
        User org = organizer(eventId);
        enableRatings(eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(formRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(formRepository.save(any())).thenAnswer(inv -> {
            RatingForm f = inv.getArgument(0);
            return RatingForm.builder()
                    .id(UUID.randomUUID()).event(event)
                    .title(f.getTitle()).collectAnonymous(f.isCollectAnonymous())
                    .sendDelayHours(f.getSendDelayHours()).questions(new ArrayList<>())
                    .build();
        });

        CreateRatingFormRequest req = new CreateRatingFormRequest();
        req.setTitle("Event Feedback");
        req.setCollectAnonymous(true);
        req.setSendDelayHours(24);

        var result = ratingService.createForm(eventId, req, org);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTitle()).isEqualTo("Event Feedback");
    }

    @Test
    void createForm_throwsWhenFormAlreadyExists() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();
        User org = organizer(eventId);
        enableRatings(eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(formRepository.findByEventId(eventId))
                .thenReturn(Optional.of(RatingForm.builder().id(UUID.randomUUID()).build()));

        CreateRatingFormRequest req = new CreateRatingFormRequest();
        req.setTitle("Another Form");

        assertThatThrownBy(() -> ratingService.createForm(eventId, req, org))
                .isInstanceOf(EventsNestException.class);

        verify(formRepository, never()).save(any());
    }

    @Test
    void createForm_throwsWhenRatingsDisabled() {
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();
        User org = organizer(eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().ratingsEnabled(false).build()));

        CreateRatingFormRequest req = new CreateRatingFormRequest();
        req.setTitle("Form");

        assertThatThrownBy(() -> ratingService.createForm(eventId, req, org))
                .isInstanceOf(RatingsNotEnabledException.class);
    }

    @Test
    void submitResponse_acceptsValidSubmission() {
        UUID formId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();
        UUID questionId = UUID.randomUUID();

        RatingQuestion question = RatingQuestion.builder().id(questionId).build();
        RatingForm form = RatingForm.builder()
                .id(formId).event(event)
                .collectAnonymous(true).questions(List.of(question)).build();

        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().ratingsEnabled(true).build()));
        when(formRepository.findById(formId)).thenReturn(Optional.of(form));
        when(responseRepository.save(any())).thenAnswer(inv -> {
            RatingResponse r = inv.getArgument(0);
            r = RatingResponse.builder()
                    .id(UUID.randomUUID()).form(form)
                    .submittedAt(LocalDateTime.now()).answers(new ArrayList<>()).build();
            return r;
        });

        AnswerRequest answerReq = new AnswerRequest();
        answerReq.setQuestionId(questionId);
        answerReq.setAnswerText("Great event!");

        SubmitRatingRequest req = new SubmitRatingRequest();
        req.setAnswers(List.of(answerReq));

        var result = ratingService.submitResponse(formId, req, null);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void submitResponse_throwsDuplicateForNonAnonymousForm() {
        UUID formId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Events event = Events.builder().id(eventId).build();
        User user = User.builder().id("user-1").build();

        RatingForm form = RatingForm.builder()
                .id(formId).event(event).collectAnonymous(false).questions(new ArrayList<>()).build();

        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().ratingsEnabled(true).build()));
        when(formRepository.findById(formId)).thenReturn(Optional.of(form));
        when(responseRepository.findByFormIdAndRespondentId(formId, "user-1"))
                .thenReturn(Optional.of(RatingResponse.builder().build()));

        SubmitRatingRequest req = new SubmitRatingRequest();
        req.setAnswers(new ArrayList<>());

        assertThatThrownBy(() -> ratingService.submitResponse(formId, req, user))
                .isInstanceOf(RatingAlreadySubmittedException.class);
    }

    @Test
    void getForm_throwsWhenFormNotFound() {
        UUID eventId = UUID.randomUUID();
        when(configRepository.findByEventId(eventId))
                .thenReturn(Optional.of(EventConfig.builder().ratingsEnabled(true).build()));
        when(formRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.getForm(eventId))
                .isInstanceOf(RatingFormNotFoundException.class);
    }
}
