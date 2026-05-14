package group.moniepoint.eventsnestserver.contracts.service;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.contracts.dto.request.AddMilestoneRequest;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowAccountResponse;
import group.moniepoint.eventsnestserver.contracts.dto.response.EscrowMilestoneResponse;

import java.util.UUID;

public interface EscrowService {

    /** Organizer funds the escrow — contract moves SIGNED → FUNDED. */
    EscrowAccountResponse fundEscrow(UUID contractId, User caller);

    EscrowAccountResponse getEscrow(UUID contractId, User caller);

    EscrowMilestoneResponse addMilestone(UUID contractId, AddMilestoneRequest request, User caller);

    /** Organizer approves milestone completion — PENDING → APPROVED. */
    EscrowMilestoneResponse approveMilestone(UUID contractId, UUID milestoneId, User caller);

    /** Organizer releases funds for an approved milestone — APPROVED → RELEASED. */
    EscrowMilestoneResponse releaseMilestone(UUID contractId, UUID milestoneId, User caller);
}
