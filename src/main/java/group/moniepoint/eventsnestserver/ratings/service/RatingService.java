package group.moniepoint.eventsnestserver.ratings.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.ratings.dto.request.AddQuestionRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.CreateRatingFormRequest;
import group.moniepoint.eventsnestserver.ratings.dto.request.SubmitRatingRequest;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingFormResponse;
import group.moniepoint.eventsnestserver.ratings.dto.response.RatingResponseView;
import group.moniepoint.eventsnestserver.ratings.model.RatingForm;

import java.util.List;
import java.util.UUID;

public interface RatingService {
    EventsNestResponse<RatingFormResponse> createForm(UUID eventId, CreateRatingFormRequest request, User organizer);
    RatingFormResponse getForm(UUID eventId);
    EventsNestResponse<RatingFormResponse> addQuestion(UUID eventId, AddQuestionRequest request, User organizer);
    EventsNestResponse<Void> deleteQuestion(UUID eventId, UUID questionId, User organizer);
    EventsNestResponse<RatingResponseView> submitResponse(UUID formId, SubmitRatingRequest request, User currentUser);
    List<RatingResponseView> getResponses(UUID eventId, User organizer);
    List<RatingForm> findFormsReadyToDispatch(int maxSendDelayHours);
    void markDispatched(UUID formId);
}
