package group.moniepoint.eventsnestserver.ratings.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventConfig;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventConfigRepository;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.EventsNestException;
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
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingAnswerResponse;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingFormResponse;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingQuestionResponse;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingResponseView;
import group.moniepoint.eventsnestserver.ratings.model.RatingAnswer;
import group.moniepoint.eventsnestserver.ratings.model.RatingForm;
import group.moniepoint.eventsnestserver.ratings.model.RatingQuestion;
import group.moniepoint.eventsnestserver.ratings.model.RatingResponse;
import group.moniepoint.eventsnestserver.ratings.repository.RatingFormRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingQuestionRepository;
import group.moniepoint.eventsnestserver.ratings.repository.RatingResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final RatingFormRepository formRepository;
    private final RatingQuestionRepository questionRepository;
    private final RatingResponseRepository responseRepository;
    private final EventRespository eventRepository;
    private final EventConfigRepository configRepository;
    private final EventMembershipRepository membershipRepository;

    @Override
    @Transactional
    public EventsNestResponse<RatingFormResponse> createForm(UUID eventId,
                                                             CreateRatingFormRequest request,
                                                             User organizer) {
        Events event = findEventOrThrow(eventId);
        assertOrganizer(eventId, organizer);
        assertRatingsEnabled(eventId);

        if (formRepository.findByEventId(eventId).isPresent()) {
            throw new EventsNestException("a rating form already exists for this event");
        }

        RatingForm form = RatingForm.builder()
                .event(event)
                .title(request.getTitle())
                .collectAnonymous(request.isCollectAnonymous())
                .sendDelayHours(request.getSendDelayHours())
                .build();

        RatingForm saved = formRepository.save(form);

        EventsNestResponse<RatingFormResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Rating form created");
        response.setData(toFormResponse(saved, 0));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public RatingFormResponse getForm(UUID eventId) {
        assertRatingsEnabled(eventId);
        RatingForm form = formRepository.findByEventId(eventId)
                .orElseThrow(RatingFormNotFoundException::new);
        long count = responseRepository.countByFormId(form.getId());
        return toFormResponse(form, count);
    }

    @Override
    @Transactional
    public EventsNestResponse<RatingFormResponse> addQuestion(UUID eventId,
                                                              AddQuestionRequest request,
                                                              User organizer) {
        assertOrganizer(eventId, organizer);
        assertRatingsEnabled(eventId);

        RatingForm form = formRepository.findByEventId(eventId)
                .orElseThrow(RatingFormNotFoundException::new);

        RatingQuestion question = RatingQuestion.builder()
                .form(form)
                .questionText(request.getQuestionText())
                .questionType(request.getQuestionType())
                .displayOrder(request.getDisplayOrder())
                .required(request.isRequired())
                .options(request.getOptions())
                .build();

        questionRepository.save(question);
        formRepository.flush();

        RatingForm refreshed = formRepository.findById(form.getId()).orElseThrow();
        long count = responseRepository.countByFormId(form.getId());

        EventsNestResponse<RatingFormResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Question added");
        response.setData(toFormResponse(refreshed, count));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> deleteQuestion(UUID eventId, UUID questionId, User organizer) {
        assertOrganizer(eventId, organizer);
        assertRatingsEnabled(eventId);

        RatingForm form = formRepository.findByEventId(eventId)
                .orElseThrow(RatingFormNotFoundException::new);

        RatingQuestion question = questionRepository.findById(questionId)
                .filter(q -> q.getForm().getId().equals(form.getId()))
                .orElseThrow(() -> new group.moniepoint.eventsnestserver.exception.ResourceNotFoundException("question not found"));

        questionRepository.delete(question);

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Question deleted");
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<RatingResponseView> submitResponse(UUID formId,
                                                                 SubmitRatingRequest request,
                                                                 User currentUser) {
        RatingForm form = formRepository.findById(formId)
                .orElseThrow(RatingFormNotFoundException::new);

        assertRatingsEnabled(form.getEvent().getId());

        String respondentId = currentUser != null ? currentUser.getId() : null;

        if (!form.isCollectAnonymous() && respondentId != null) {
            if (responseRepository.findByFormIdAndRespondentId(formId, respondentId).isPresent()) {
                throw new RatingAlreadySubmittedException();
            }
        }

        // Index questions for answer validation
        Map<UUID, RatingQuestion> questionMap = form.getQuestions().stream()
                .collect(Collectors.toMap(RatingQuestion::getId, q -> q));

        RatingResponse ratingResponse = RatingResponse.builder()
                .form(form)
                .respondentId(form.isCollectAnonymous() ? null : respondentId)
                .submittedAt(LocalDateTime.now())
                .build();

        for (AnswerRequest answerReq : request.getAnswers()) {
            RatingQuestion question = questionMap.get(answerReq.getQuestionId());
            if (question == null) continue;

            RatingAnswer answer = RatingAnswer.builder()
                    .response(ratingResponse)
                    .question(question)
                    .answerText(answerReq.getAnswerText())
                    .answerNumber(answerReq.getAnswerNumber())
                    .build();
            ratingResponse.getAnswers().add(answer);
        }

        RatingResponse saved = responseRepository.save(ratingResponse);

        EventsNestResponse<RatingResponseView> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Rating submitted — thank you for your feedback!");
        response.setData(toResponseView(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RatingResponseView> getResponses(UUID eventId, User organizer) {
        assertOrganizer(eventId, organizer);
        assertRatingsEnabled(eventId);

        RatingForm form = formRepository.findByEventId(eventId)
                .orElseThrow(RatingFormNotFoundException::new);

        return responseRepository.findAllByFormId(form.getId())
                .stream().map(this::toResponseView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RatingForm> findFormsReadyToDispatch(int maxSendDelayHours) {
        // Use a conservative threshold: now minus the maximum possible delay.
        // The scheduler checks per-form delay anyway.
        LocalDateTime threshold = LocalDateTime.now().minusHours(maxSendDelayHours);
        return formRepository.findFormsReadyToDispatch(threshold);
    }

    @Override
    @Transactional
    public void markDispatched(UUID formId) {
        RatingForm form = formRepository.findById(formId).orElseThrow();
        form.setSentAt(LocalDateTime.now());
        formRepository.save(form);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void assertOrganizer(UUID eventId, User user) {
        if (!membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER)) {
            throw new NotEventOrganizerException();
        }
    }

    private void assertRatingsEnabled(UUID eventId) {
        EventConfig config = configRepository.findByEventId(eventId)
                .orElseThrow(EventConfigNotFoundException::new);
        if (!config.isRatingsEnabled()) {
            throw new RatingsNotEnabledException();
        }
    }

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    static RatingFormResponse toFormResponse(RatingForm form, long responseCount) {
        List<RatingQuestionResponse> questions = form.getQuestions().stream()
                .map(q -> RatingQuestionResponse.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .questionType(q.getQuestionType())
                        .displayOrder(q.getDisplayOrder())
                        .required(q.isRequired())
                        .options(q.getOptions())
                        .build())
                .toList();

        return RatingFormResponse.builder()
                .id(form.getId())
                .eventId(form.getEvent().getId())
                .title(form.getTitle())
                .collectAnonymous(form.isCollectAnonymous())
                .sendDelayHours(form.getSendDelayHours())
                .sentAt(form.getSentAt())
                .responseCount(responseCount)
                .questions(questions)
                .createdAt(form.getCreatedAt())
                .build();
    }

    private RatingResponseView toResponseView(RatingResponse resp) {
        List<RatingAnswerResponse> answers = resp.getAnswers().stream()
                .map(a -> RatingAnswerResponse.builder()
                        .questionId(a.getQuestion().getId())
                        .questionText(a.getQuestion().getQuestionText())
                        .answerText(a.getAnswerText())
                        .answerNumber(a.getAnswerNumber())
                        .build())
                .toList();

        return RatingResponseView.builder()
                .id(resp.getId())
                .respondentId(resp.getRespondentId())
                .submittedAt(resp.getSubmittedAt())
                .answers(answers)
                .build();
    }
}
