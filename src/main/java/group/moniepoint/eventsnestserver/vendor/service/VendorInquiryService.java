package group.moniepoint.eventsnestserver.vendor.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.vendor.dto.request.CreateInquiryRequest;
import group.moniepoint.eventsnestserver.vendor.dto.response.VendorInquiryResponse;

import java.util.List;
import java.util.UUID;

public interface VendorInquiryService {

    /** Organizer sends an inquiry to a vendor for an event. Auto-creates a DM conversation. */
    VendorInquiryResponse sendInquiry(UUID eventId, CreateInquiryRequest request, User caller);

    /** All inquiries the organizer sent for a given event. */
    List<VendorInquiryResponse> listSentInquiries(UUID eventId, User caller);

    /** All inquiries the calling vendor has received. */
    List<VendorInquiryResponse> listReceivedInquiries(User caller);

    /** Organizer closes the inquiry — PENDING → CLOSED. */
    VendorInquiryResponse closeInquiry(UUID inquiryId, User caller);
}
