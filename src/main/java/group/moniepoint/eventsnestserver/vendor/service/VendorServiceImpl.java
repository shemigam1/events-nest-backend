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
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorMarketplaceResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorProfileResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorScheduleItem;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorVerificationResponse;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.vendor.model.VendorRating;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import group.moniepoint.eventsnestserver.vendor.repository.VendorRatingRepository;
import group.moniepoint.eventsnestserver.audit.AuditAction;
import group.moniepoint.eventsnestserver.audit.AuditEntityType;
import group.moniepoint.eventsnestserver.audit.event.AuditEvent;
import group.moniepoint.eventsnestserver.audit.publisher.AuditEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

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
    private final AuditEventPublisher auditEventPublisher;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "vendor-marketplace", key = "#serviceType != null ? #serviceType.toLowerCase() : 'all'")
    public List<VendorMarketplaceResponse> listMarketplace(String serviceType) {
        List<User> vendors = (serviceType != null && !serviceType.isBlank())
                ? userRepository.findVerifiedVendorsByServiceType(serviceType)
                : userRepository.findVerifiedVendors();

        if (vendors.isEmpty()) return List.of();

        List<String> vendorIds = vendors.stream().map(User::getId).toList();

        // Bulk-fetch rating aggregates: vendorId → [avg, count]
        Map<String, double[]> ratingMap = ratingRepository
                .findAggregatesByVendorIds(vendorIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> new double[]{
                                ((Number) row[1]).doubleValue(),
                                ((Number) row[2]).doubleValue()
                        }));

        // Bulk-fetch completed event counts: vendorId → count
        Map<String, Long> completedMap = applicationRepository
                .countCompletedByVendorIds(vendorIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()));

        return vendors.stream()
                .map(vendor -> {
                    double[] agg = ratingMap.get(vendor.getId());
                    Double avg   = agg != null ? agg[0] : null;
                    long count   = agg != null ? (long) agg[1] : 0L;
                    long done    = completedMap.getOrDefault(vendor.getId(), 0L);
                    return VendorMarketplaceResponse.from(vendor, avg, count, done);
                })
                .toList();
    }

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

    @Override
    @Transactional(readOnly = true)
    public VendorProfileResponse getVendorProfile(String vendorId, User caller) {
        User vendor = userRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        // Fetch all accepted applications for this vendor
        List<VendorApplication> accepted = applicationRepository
                .findAcceptedByVendorIdOrderByEventStart(vendorId);

        // Vendor profiles are part of the public marketplace — anyone who
        // can browse /vendors can drill into one. The earlier "must be
        // organiser of an event this vendor worked" guard defeated the
        // marketplace's whole purpose (prospective clients couldn't review
        // a vendor before engaging). `caller` is retained for future use
        // (e.g. hiding agreedAmount from third parties) but doesn't gate
        // access today.

        // Fetch all ratings for this vendor's applications in one query
        Map<UUID, VendorRating> ratingsByAppId = accepted.stream()
                .map(app -> ratingRepository.findByVendorApplicationId(app.getId()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toMap(r -> r.getVendorApplication().getId(), r -> r));

        LocalDateTime now = LocalDateTime.now();
        List<VendorScheduleItem> upcoming = accepted.stream()
                .filter(app -> app.getEvent().getStartTime().isAfter(now))
                .map(app -> VendorScheduleItem.from(app, ratingsByAppId.get(app.getId())))
                .toList();

        List<VendorScheduleItem> completed = accepted.stream()
                .filter(app -> app.getEvent().getEndTime().isBefore(now))
                .map(app -> VendorScheduleItem.from(app, ratingsByAppId.get(app.getId())))
                .toList();

        Double avg = ratingRepository.findAverageScoreByVendorId(vendorId);
        long totalRatings = ratingRepository.countByVendorId(vendorId);

        return VendorProfileResponse.builder()
                .vendorId(vendor.getId())
                .vendorName(vendor.getFirstName() + " " + vendor.getLastName())
                .email(vendor.getEmail())
                .serviceType(vendor.getVendorServiceType())
                .profileDescription(vendor.getVendorProfileDescription())
                .vendorVerified(vendor.isVendorVerified())
                .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null)
                .totalRatings(totalRatings)
                .upcomingSchedule(upcoming)
                .completedWork(completed)
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = "vendor-marketplace", allEntries = true)
    public VendorApplicationResponse rateVendor(UUID eventId, UUID applicationId,
                                                 RateVendorRequest request, User caller) {
        assertOrganizer(eventId, caller);

        VendorApplication app = findApplication(eventId, applicationId);

        if (app.getStatus() != VendorApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("Only accepted vendor applications can be rated");
        }
        if (app.getEvent().getEndTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("You can only rate a vendor after the event has ended");
        }

        VendorRating rating = ratingRepository.findByVendorApplicationId(applicationId)
                .orElseGet(() -> VendorRating.builder()
                        .vendorApplication(app)
                        .ratedBy(caller)
                        .build());

        rating.setScore(request.getScore());
        rating.setComment(request.getComment());
        ratingRepository.save(rating);

        return VendorApplicationResponse.from(app);
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
    @CacheEvict(value = "vendor-marketplace", allEntries = true)
    public VendorVerificationResponse approveVerification(String userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setVendorVerified(true);
        user.setVendorVerificationStatus(VendorVerificationStatus.VERIFIED);
        user.setVendorVerificationRejectionReason(null);
        user.setVendorVerifiedAt(LocalDateTime.now());
        VendorVerificationResponse result = VendorVerificationResponse.from(userRepository.save(user));

        auditEventPublisher.publish(AuditEvent.of(
                currentActorId(), currentActorRole(),
                AuditAction.APPROVE_VENDOR_VERIFICATION, AuditEntityType.USER, userId,
                Map.of("serviceType", user.getVendorServiceType() != null ? user.getVendorServiceType() : "")));

        return result;
    }

    @Override
    @Transactional
    @CacheEvict(value = "vendor-marketplace", allEntries = true)
    public VendorVerificationResponse rejectVerification(String userId, String reason) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setVendorVerified(false);
        user.setVendorVerificationStatus(VendorVerificationStatus.REJECTED);
        user.setVendorVerificationRejectionReason(reason);
        user.setVendorVerifiedAt(null);
        VendorVerificationResponse result = VendorVerificationResponse.from(userRepository.save(user));

        auditEventPublisher.publish(AuditEvent.of(
                currentActorId(), currentActorRole(),
                AuditAction.REJECT_VENDOR_VERIFICATION, AuditEntityType.USER, userId,
                Map.of("reason", reason != null ? reason : "")));

        return result;
    }

    // ─── audit helpers ────────────────────────────────────────────────────────

    private String currentActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
    }

    private String currentActorRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse(null);
    }

    private void assertOrganizer(UUID eventId, User user) {
        if (!membershipRepository.existsByEventsIdAndUserIdAndRole(
                eventId, user.getId(), EventRole.ORGANIZER)) {
            throw new UnauthorizedException("Only the event organiser can rate a vendor");
        }
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
