package group.moniepoint.eventsnestserver.admin.service;

import group.moniepoint.eventsnestserver.admin.dto.request.CompleteAdminInvitationRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.InviteAdminRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.RejectEventRequest;
import group.moniepoint.eventsnestserver.admin.dto.request.UpdateUserStatusRequest;
import group.moniepoint.eventsnestserver.admin.dto.response.EventEditRequestResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.PageResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.PlatformAnalyticsResponse;
import group.moniepoint.eventsnestserver.admin.dto.response.UserSummaryResponse;
import group.moniepoint.eventsnestserver.bookings.dto.response.BookingResponse;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventEditStatus;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.manager.dto.response.ManagerResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AdminService {

    PageResponse<EventResponse> getEventsByStatus(EventStatus status, Pageable pageable);

    EventsNestResponse<EventResponse> approveEvent(UUID eventId);

    EventsNestResponse<EventResponse> rejectEvent(UUID eventId, RejectEventRequest request);

    PageResponse<UserSummaryResponse> getUsers(Pageable pageable);

    PlatformAnalyticsResponse getAnalytics();

    EventsNestResponse<EventResponse> getEventById(UUID eventId);

    EventsNestResponse<UserSummaryResponse> getUserById(String userId);

    EventsNestResponse<UserSummaryResponse> updateUserStatus(String userId, UpdateUserStatusRequest request);

    EventsNestResponse<EventResponse> cancelEvent(UUID eventId);

    List<BookingResponse> getEventBookings(UUID eventId);

    PageResponse<EventEditRequestResponse> getEventEditRequests(EventEditStatus status, Pageable pageable);

    EventsNestResponse<EventResponse> approveEventUpdate(UUID editRequestId);

    EventsNestResponse<EventResponse> rejectEventUpdate(UUID editRequestId, RejectEventRequest request);

    EventsNestResponse<Void> inviteAdmin(InviteAdminRequest request);

    EventsNestResponse<UserSummaryResponse> completeAdminInvitation(CompleteAdminInvitationRequest request);

    // ─── Vendor management (admin override) ──────────────────────────────────────

    /** Admin can approve any vendor application without being an organizer/manager. */
    EventsNestResponse<VendorApplicationResponse> approveVendorApplication(UUID eventId, UUID applicationId);

    /** Admin can reject any vendor application without being an organizer/manager. */
    EventsNestResponse<VendorApplicationResponse> rejectVendorApplication(UUID eventId, UUID applicationId);

    // ─── Manager management (admin override) ─────────────────────────────────────

    /** Admin can assign any user as manager for any event. */
    EventsNestResponse<ManagerResponse> assignManagerToEvent(UUID eventId, String userId);

    /** Admin can remove any manager from any event. */
    EventsNestResponse<Void> removeManagerFromEvent(UUID eventId, String userId);
}
