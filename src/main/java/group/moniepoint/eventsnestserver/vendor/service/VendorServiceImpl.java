package group.moniepoint.eventsnestserver.vendor.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import group.moniepoint.eventsnestserver.events.models.EventRole;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.events.repository.EventMembershipRepository;
import group.moniepoint.eventsnestserver.events.repository.EventRespository;
import group.moniepoint.eventsnestserver.exception.ResourceNotFoundException;
import group.moniepoint.eventsnestserver.exception.UnauthorizedException;
import group.moniepoint.eventsnestserver.exception.auth.UserNotFoundException;
import group.moniepoint.eventsnestserver.exception.event.EventNotFoundException;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyForVendorVerificationRequest;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyAsVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.request.RateVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorProfileResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorScheduleItem;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorVerificationResponse;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.vendor.model.VendorRating;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import group.moniepoint.eventsnestserver.vendor.repository.VendorRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorApplicationRepository applicationRepository;
    private final VendorRatingRepository ratingRepository;
    private final EventRespository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public VendorApplicationResponse apply(UUID eventId, ApplyAsVendorRequest request, User caller) {
        Events event = eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);

        if (applicationRepository.existsByEventIdAndApplicantIdAndServiceType(
                eventId, caller.getId(), request.getServiceType())) {
            throw new IllegalStateException(
                    "You already have a pending or accepted application for this service type");
        }

        VendorApplication application = VendorApplication.builder()
                .event(event)
                .applicant(caller)
                .serviceType(request.getServiceType())
                .description(request.getDescription())
                .proposedAmount(request.getProposedAmount())
                .status(VendorApplicationStatus.PENDING)
                .build();

        return VendorApplicationResponse.from(applicationRepository.save(application));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorApplicationResponse> listApplicationsForEvent(UUID eventId, String status, User caller) {
        assertOrganizerOrManager(eventId, caller);

        List<VendorApplication> apps;
        if (status != null && !status.isBlank()) {
            VendorApplicationStatus filter = VendorApplicationStatus.valueOf(status.toUpperCase());
            apps = applicationRepository.findAllByEventIdAndStatusOrderByCreatedAtDesc(eventId, filter);
        } else {
            apps = applicationRepository.findAllByEventIdOrderByCreatedAtDesc(eventId);
        }

        return apps.stream().map(VendorApplicationResponse::from).toList();
    }

    @Override
    @Transactional
    public VendorApplicationResponse accept(UUID eventId, UUID applicationId, User caller) {
        assertOrganizerOrManager(eventId, caller);
        VendorApplication app = findApplication(eventId, applicationId);
        app.setStatus(VendorApplicationStatus.ACCEPTED);
        app.setReviewedBy(caller);
        app.setReviewedAt(LocalDateTime.now());
        return VendorApplicationResponse.from(applicationRepository.save(app));
    }

    @Override
    @Transactional
    public VendorApplicationResponse reject(UUID eventId, UUID applicationId, User caller) {
        assertOrganizerOrManager(eventId, caller);
        VendorApplication app = findApplication(eventId, applicationId);
        app.setStatus(VendorApplicationStatus.REJECTED);
        app.setReviewedBy(caller);
        app.setReviewedAt(LocalDateTime.now());
        return VendorApplicationResponse.from(applicationRepository.save(app));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorApplicationResponse> listMyApplications(User caller) {
        return applicationRepository.findAllByApplicantIdOrderByCreatedAtDesc(caller.getId())
                .stream().map(VendorApplicationResponse::from).toList();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public VendorVerificationResponse applyForVerification(ApplyForVendorVerificationRequest request, User caller) {
        if (caller.getVendorVerificationStatus() == VendorVerificationStatus.VERIFIED) {
            throw new IllegalStateException("This account is already verified as a vendor");
        }
        if (caller.getVendorVerificationStatus() == VendorVerificationStatus.PENDING) {
            throw new IllegalStateException("Vendor verification request is already pending");
        }

        caller.setVendorVerified(false);
        caller.setVendorVerificationStatus(VendorVerificationStatus.PENDING);
        caller.setVendorServiceType(request.getServiceType());
        caller.setVendorProfileDescription(request.getDescription());
        caller.setVendorVerificationRejectionReason(null);
        caller.setVendorVerificationSubmittedAt(LocalDateTime.now());
        caller.setVendorVerifiedAt(null);

        return VendorVerificationResponse.from(userRepository.save(caller));
    }

    @Override
    @Transactional(readOnly = true)
    public VendorVerificationResponse getMyVerification(User caller) {
        return VendorVerificationResponse.from(caller);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorVerificationResponse> listVerificationRequests(VendorVerificationStatus status) {
        return userRepository.findAllByVendorVerificationStatusOrderByVendorVerificationSubmittedAtAsc(status)
                .stream()
                .map(VendorVerificationResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public VendorVerificationResponse approveVerification(String userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setVendorVerified(true);
        user.setVendorVerificationStatus(VendorVerificationStatus.VERIFIED);
        user.setVendorVerificationRejectionReason(null);
        user.setVendorVerifiedAt(LocalDateTime.now());
        return VendorVerificationResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public VendorVerificationResponse rejectVerification(String userId, String reason) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setVendorVerified(false);
        user.setVendorVerificationStatus(VendorVerificationStatus.REJECTED);
        user.setVendorVerificationRejectionReason(reason);
        user.setVendorVerifiedAt(null);
        return VendorVerificationResponse.from(userRepository.save(user));
    }

    private void assertOrganizerOrManager(UUID eventId, User user) {
        boolean isOrganizer = membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, user.getId(), EventRole.ORGANIZER);
        boolean isManager   = membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, user.getId(), EventRole.MANAGER);
        if (!isOrganizer && !isManager) {
            throw new UnauthorizedException("Only the event organiser or manager can manage vendor applications");
        }
    }

    private VendorApplication findApplication(UUID eventId, UUID applicationId) {
        VendorApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (!app.getEvent().getId().equals(eventId)) {
            throw new ResourceNotFoundException("Application does not belong to this event");
        }
        return app;
    }
}
