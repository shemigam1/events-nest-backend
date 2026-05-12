package group.moniepoint.eventsnestserver.manager.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;
import group.moniepoint.eventsnestserver.events.dto.response.EventResponse;
import group.moniepoint.eventsnestserver.events.models.EventMembership;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.models.MembershipStatus;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.auth.NotEventOrganizerException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.manager.dto.request.AssignManagerRequest;
import group.moniepoint.eventsnestserver.manager.dto.response.ManagerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerServiceImpl implements ManagerService {

    private final EventMembershipRepository membershipRepository;
    private final EventRespository eventRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public EventsNestResponse<ManagerResponse> assignManager(UUID eventId,
                                                             AssignManagerRequest request,
                                                             User organizer) {
        Events event = findEventOrThrow(eventId);
        assertOrganizer(eventId, organizer);

        User candidate = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user found with email: " + request.getEmail()));

        if (candidate.getId().equals(organizer.getId())) {
            throw new IllegalArgumentException("organizers cannot assign themselves as manager");
        }

        // Idempotent — silently return existing if already assigned
        boolean alreadyManager = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER);
        if (alreadyManager) {
            EventMembership existing = membershipRepository
                    .findByEventsIdAndUserIdAndRole(eventId, candidate.getId(), EventRole.MANAGER)
                    .orElseThrow();
            EventsNestResponse<ManagerResponse> response = new EventsNestResponse<>();
            response.setSuccess(true);
            response.setMessage("User is already a manager for this event");
            response.setData(toManagerResponse(existing));
            return response;
        }

        EventMembership membership = EventMembership.builder()
                .user(candidate)
                .events(event)
                .role(EventRole.MANAGER)
                .status(MembershipStatus.ACTIVE)
                .assignedBy(organizer)
                .build();

        EventMembership saved = membershipRepository.save(membership);
        log.info("Manager {} assigned to event {} by organizer {}", candidate.getId(), eventId, organizer.getId());

        EventsNestResponse<ManagerResponse> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Manager assigned successfully");
        response.setData(toManagerResponse(saved));
        return response;
    }

    @Override
    @Transactional
    public EventsNestResponse<Void> removeManager(UUID eventId, String managerId, User organizer) {
        findEventOrThrow(eventId);
        assertOrganizer(eventId, organizer);

        boolean exists = membershipRepository
                .existsByEventsIdAndUserIdAndRole(eventId, managerId, EventRole.MANAGER);
        if (!exists) {
            throw new ResourceNotFoundException("Manager assignment not found");
        }

        membershipRepository.deleteByEventsIdAndUserIdAndRole(eventId, managerId, EventRole.MANAGER);
        log.info("Manager {} removed from event {} by organizer {}", managerId, eventId, organizer.getId());

        EventsNestResponse<Void> response = new EventsNestResponse<>();
        response.setSuccess(true);
        response.setMessage("Manager removed successfully");
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerResponse> getManagersForEvent(UUID eventId, User organizer) {
        findEventOrThrow(eventId);
        assertOrganizer(eventId, organizer);

        return membershipRepository.findAllByEventsIdAndRole(eventId, EventRole.MANAGER)
                .stream()
                .map(this::toManagerResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> getManagedEvents(User manager) {
        return membershipRepository.findAllByUserIdAndRole(manager.getId(), EventRole.MANAGER)
                .stream()
                .map(m -> toEventResponse(m.getEvents()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getManagedEventById(UUID eventId, User manager) {
        assertManager(eventId, manager);
        Events event = findEventOrThrow(eventId);
        return toEventResponse(event);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void assertOrganizer(UUID eventId, User user) {
        if (!membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.ORGANIZER)) {
            throw new NotEventOrganizerException();
        }
    }

    private void assertManager(UUID eventId, User user) {
        if (!membershipRepository.existsByEventsIdAndUserIdAndRole(eventId, user.getId(), EventRole.MANAGER)) {
            throw new UnauthorizedException("you are not a manager for this event");
        }
    }

    private Events findEventOrThrow(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }

    private ManagerResponse toManagerResponse(EventMembership m) {
        User u = m.getUser();
        ManagerResponse.ManagerResponseBuilder builder = ManagerResponse.builder()
                .membershipId(m.getId())
                .userId(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .assignedAt(m.getCreatedAt());

        if (m.getAssignedBy() != null) {
            User ab = m.getAssignedBy();
            builder.assignedByUserId(ab.getId())
                    .assignedByName(trim(ab.getFirstName()) + " " + trim(ab.getLastName()));
        }
        return builder.build();
    }

    private EventResponse toEventResponse(Events e) {
        return modelMapper.map(e, EventResponse.class);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
