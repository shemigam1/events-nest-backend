package group.moniepoint.eventsnestserver.vendor.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyForVendorVerificationRequest;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyAsVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.request.RateVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorMarketplaceResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorProfileResponse;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorVerificationResponse;

import java.util.List;
import java.util.UUID;

public interface VendorService {

    /**
     * Marketplace: list all platform-verified vendors.
     * Optional {@code serviceType} filter (partial, case-insensitive).
     */
    List<VendorMarketplaceResponse> listMarketplace(String serviceType);

    /** Any authenticated user can apply to provide a service for an event. */
    VendorApplicationResponse apply(UUID eventId, ApplyAsVendorRequest request, User caller);

    /** Organiser/Manager: list all applications for their event. */
    List<VendorApplicationResponse> listApplicationsForEvent(UUID eventId, String status, User caller);

    /** Organiser/Manager: accept a vendor application. */
    VendorApplicationResponse accept(UUID eventId, UUID applicationId, User caller);

    /** Organiser/Manager: reject a vendor application. */
    VendorApplicationResponse reject(UUID eventId, UUID applicationId, User caller);

    /** Vendor: list my own applications. */
    List<VendorApplicationResponse> listMyApplications(User caller);

    /**
     * Organiser/Manager: full profile of a vendor — bio, average rating, upcoming
     * schedule, and completed work. Caller must be an organiser or manager on at
     * least one event where this vendor has an ACCEPTED application.
     */
    VendorProfileResponse getVendorProfile(String vendorId, User caller);

    /**
     * Organiser: rate a vendor after their event has ended. Creates or updates
     * the rating — organiser can revise their score up until it is finalised.
     * The event must have already ended before a rating can be submitted.
     */
    VendorApplicationResponse rateVendor(UUID eventId, UUID applicationId, RateVendorRequest request, User caller);

    VendorVerificationResponse applyForVerification(ApplyForVendorVerificationRequest request, User caller);

    VendorVerificationResponse getMyVerification(User caller);

    List<VendorVerificationResponse> listVerificationRequests(VendorVerificationStatus status);

    VendorVerificationResponse approveVerification(String userId);

    VendorVerificationResponse rejectVerification(String userId, String reason);
}
