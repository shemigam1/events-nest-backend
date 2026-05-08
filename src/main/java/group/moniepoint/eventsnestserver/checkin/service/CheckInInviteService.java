package group.moniepoint.eventsnestserver.checkin.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.checkin.dto.request.CreateCheckInInviteRequest;
import group.moniepoint.eventsnestserver.checkin.dto.response.CheckInInviteResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

import java.util.List;
import java.util.UUID;

public interface CheckInInviteService {
    EventsNestResponse<CheckInInviteResponse> createInvite(UUID eventId, CreateCheckInInviteRequest request, User organizer);
    List<CheckInInviteResponse> listInvites(UUID eventId, User organizer);
    EventsNestResponse<Void> revokeInvite(UUID eventId, UUID inviteId, User organizer);
}
