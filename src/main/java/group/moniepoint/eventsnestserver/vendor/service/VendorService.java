package group.moniepoint.eventsnestserver.vendor.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.vendor.dto.request.ApplyAsVendorRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorApplicationResponse;

import java.util.List;
import java.util.UUID;

public interface VendorService {

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
}
