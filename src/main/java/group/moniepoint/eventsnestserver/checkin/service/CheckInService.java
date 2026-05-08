package group.moniepoint.eventsnestserver.checkin.service;

import group.moniepoint.eventsnestserver.checkin.dto.request.CheckInRequest;
import group.moniepoint.eventsnestserver.checkin.dto.response.CheckInResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

import java.util.UUID;

public interface CheckInService {
    EventsNestResponse<CheckInResponse> checkIn(UUID eventId, CheckInRequest request);
}
