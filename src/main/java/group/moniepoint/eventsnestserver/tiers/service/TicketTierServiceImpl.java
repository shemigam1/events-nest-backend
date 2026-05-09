package group.moniepoint.eventsnestserver.tiers.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.events.models.EventStatus;
import group.moniepoint.eventsnestserver.exception.EventFieldLockedException;
import group.moniepoint.eventsnestserver.exception.EventNotFoundException;
import group.moniepoint.eventsnestserver.exception.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.TicketTierNotFoundException;
import group.moniepoint.eventsnestserver.exception.TierNotEditableException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.tiers.dto.request.CreateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.request.UpdateTicketTierRequest;
import group.moniepoint.eventsnestserver.tiers.dto.response.TicketTierResponse;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import group.moniepoint.eventsnestserver.tiers.repository.TicketTierRepository;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TicketTierServiceImpl implements TicketTierService {

    private final ModelMapper modelMapper;
    private final TicketTierRepository tierRepository;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;

    @Override
    @Transactional
    public EventsNestResponse<TicketTierResponse> createTier(UUID eventId,
                                                             CreateTicketTierRequest request,
                                                             User requestingUser) {
        Events event = findEventOrThrow(eventId);
        assertIsOrganizer(event, requestingUser);

        int totalCapacity = request.getRowCount() * request.getSeatsPerRow();

        TicketTier tier = TicketTier.builder()
                .event(event)
                .name(request.getName())
                .price(request.getPrice())
                .rowPrefix(request.getRowPrefix())
                .rowCount(request.getRowCount())
                .seatsPerRow(request.getSeatsPerRow())
                .totalCapacity(totalCapacity)
                .availableCapacity(totalCapacity)
                .build();

        TicketTier saved = tierRepository.saveAndFlush(tier);

        EventsNestResponse<TicketTierResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Ticket tier created successfully");
        response.setData(toTierResponse(saved));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTierResponse> getTiersByEvent(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException();
        }
        return tierRepository.findAllByEventId(eventId)
                .stream()
                .map(this::toTierResponse)
                .toList();
    }

    @Override
    @Transactional
    public EventsNestResponse<TicketTierResponse> updateTier(UUID eventId,
                                                             UUID tierId,
                                                             UpdateTicketTierRequest request,
                                                             User requestingUser) {
        TicketTier tier = findTierForEventOrThrow(eventId, tierId);
        assertIsOrganizer(tier.getEvent(), requestingUser);

        if (tier.getEvent().getStatus() == EventStatus.PUBLISHED) {
            int soldCount = tier.getTotalCapacity() - tier.getAvailableCapacity();
            if (soldCount > 0) {
                throw new TierNotEditableException(soldCount);
            }
            if (request.getRowCount() != null)   throw new EventFieldLockedException("rowCount");
            if (request.getSeatsPerRow() != null) throw new EventFieldLockedException("seatsPerRow");
            if (request.getRowPrefix() != null)  throw new EventFieldLockedException("rowPrefix");
        }

        if (request.getName() != null) tier.setName(request.getName());
        if (request.getPrice() != null) tier.setPrice(request.getPrice());

        TicketTier saved = tierRepository.saveAndFlush(tier);

        EventsNestResponse<TicketTierResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Ticket tier updated successfully");
        response.setData(toTierResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public void deleteTier(UUID eventId, UUID tierId, User requestingUser) {
        TicketTier tier = findTierForEventOrThrow(eventId, tierId);
        assertIsOrganizer(tier.getEvent(), requestingUser);

        // PRD §3.3: deletion only permitted if no bookings exist for this tier.
        // Booking module is not yet implemented — when added, check booking count here
        // and throw InvalidEventStateException if any bookings reference this tier.

        tierRepository.delete(tier);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
    }

    private TicketTier findTierForEventOrThrow(UUID eventId, UUID tierId) {
        TicketTier tier = tierRepository.findById(tierId)
                .orElseThrow(TicketTierNotFoundException::new);
        if (!tier.getEvent().getId().equals(eventId)) {
            throw new TicketTierNotFoundException();
        }
        return tier;
    }

    private void assertIsOrganizer(Events event, User user) {
        boolean isOrganizer = membershipRepository
                .existsByEventsIdAndUserIdAndRole(event.getId(), user.getId(), EventRole.ORGANIZER);
        if (!isOrganizer) {
            throw new NotEventOrganizerException();
        }
    }

    private TicketTierResponse toTierResponse(TicketTier tier) {
        TicketTierResponse response = modelMapper.map(tier, TicketTierResponse.class);
        if (tier.getEvent() != null) {
            response.setEventId(tier.getEvent().getId());
        }
        return response;
    }
}
