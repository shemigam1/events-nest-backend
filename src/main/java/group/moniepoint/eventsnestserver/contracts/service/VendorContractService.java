package group.moniepoint.eventsnestserver.contracts.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.contracts.dto.request.CreateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.request.UpdateContractRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.VendorContractResponse;

import java.util.List;
import java.util.UUID;

public interface VendorContractService {

    VendorContractResponse createContract(UUID eventId, CreateContractRequest request, User caller);

    VendorContractResponse updateContract(UUID contractId, UpdateContractRequest request, User caller);

    VendorContractResponse getContract(UUID contractId, User caller);

    List<VendorContractResponse> listByEvent(UUID eventId, User caller);

    List<VendorContractResponse> listMine(User caller);

    /** Vendor signs — DRAFT → SIGNED. Only the target vendor can sign. */
    VendorContractResponse signContract(UUID contractId, User caller);

    /** Organizer marks work as started — FUNDED → ACTIVE. */
    VendorContractResponse activateContract(UUID contractId, User caller);

    /** Organizer marks work as done — ACTIVE → COMPLETED. */
    VendorContractResponse completeContract(UUID contractId, User caller);

    /** Either party terminates — any state except COMPLETED. */
    VendorContractResponse terminateContract(UUID contractId, User caller);
}
