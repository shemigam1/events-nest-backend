package group.moniepoint.eventsnestserver.programme.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.programme.dto.request.CreateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.dto.request.UpdateProgrammeItemRequest;
import group.moniepoint.eventsnestserver.programme.dto.response.ProgrammeItemResponse;

import java.util.List;
import java.util.UUID;

public interface ProgrammeService {
    EventsNestResponse<ProgrammeItemResponse> addItem(UUID eventId, CreateProgrammeItemRequest request, User organizer);
    List<ProgrammeItemResponse> getItems(UUID eventId);
    EventsNestResponse<ProgrammeItemResponse> updateItem(UUID eventId, UUID itemId, UpdateProgrammeItemRequest request, User organizer);
    EventsNestResponse<Void> deleteItem(UUID eventId, UUID itemId, User organizer);
}
