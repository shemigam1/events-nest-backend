package group.moniepoint.eventsnestserver.admin.service;

import group.moniepoint.eventsnestserver.admin.dto.request.CompleteAdminInvitationRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.InviteAdminRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.PlatformAnalyticsResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.UserSummaryResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminService {

    PageResponse<EventResponse> getEventsByStatus(EventStatus status, Pageable pageable);

    EventsNestResponse<EventResponse> approveEvent(UUID eventId);

    EventsNestResponse<EventResponse> rejectEvent(UUID eventId, RejectEventRequest request);

    PageResponse<UserSummaryResponse> getUsers(Pageable pageable);

    PlatformAnalyticsResponse getAnalytics();

    EventsNestResponse<Void> inviteAdmin(InviteAdminRequest request);

    EventsNestResponse<UserSummaryResponse> completeAdminInvitation(CompleteAdminInvitationRequest request);
}
