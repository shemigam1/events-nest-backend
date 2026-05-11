package group.moniepoint.eventsnestserver.guestlist.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.guestlist.dto.request.CreateGuestRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.RsvpRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.request.UpdateGuestStatusRequest;
import group.moniepoint.eventsnestserver.guestlist.dto.response.GuestResponse;
import group.moniepoint.eventsnestserver.guestlist.model.Guest;

import java.util.List;
import java.util.UUID;

public interface GuestService {
    EventsNestResponse<GuestResponse> addGuest(UUID eventId, CreateGuestRequest request, User organizer);
    List<GuestResponse> getGuests(UUID eventId, User organizer);
    EventsNestResponse<GuestResponse> updateGuestStatus(UUID eventId, UUID guestId, UpdateGuestStatusRequest request, User organizer);
    EventsNestResponse<Void> removeGuest(UUID eventId, UUID guestId, User organizer);
    EventsNestResponse<GuestResponse> rsvp(RsvpRequest request);
    List<Guest> getRawGuests(UUID eventId);
}
