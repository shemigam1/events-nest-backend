package group.moniepoint.eventsnestserver.tiers.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.tiers.dto.request.CreateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.request.UpdateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;

import java.util.List;
import java.util.UUID;

public interface TicketTierService {

    EventsNestResponse<TicketTierResponse> createTier(UUID eventId, CreateTicketTierRequest request, User requestingUser);

    List<TicketTierResponse> getTiersByEvent(UUID eventId);

    EventsNestResponse<TicketTierResponse> updateTier(UUID eventId, UUID tierId, UpdateTicketTierRequest request, User requestingUser);

    void deleteTier(UUID eventId, UUID tierId, User requestingUser);
}
