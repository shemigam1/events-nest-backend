package group.moniepoint.eventsnestserver.contracts.dto.response;

import group.moniepoint.eventsnestserver.contracts.model.EscrowAccount;
import group.moniepoint.eventsnestserver.contracts.model.EscrowStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class EscrowAccountResponse {

    private UUID id;
    private UUID contractId;
    private BigDecimal totalAmount;
    private BigDecimal releasedAmount;
    private BigDecimal pendingAmount;
    private EscrowStatus status;
    private LocalDateTime fundedAt;
    private List<EscrowMilestoneResponse> milestones;
    private LocalDateTime createdAt;

    public static EscrowAccountResponse from(EscrowAccount e) {
        BigDecimal pending = e.getTotalAmount().subtract(e.getReleasedAmount());
        return EscrowAccountResponse.builder()
                .id(e.getId())
                .contractId(e.getContract().getId())
                .totalAmount(e.getTotalAmount())
                .releasedAmount(e.getReleasedAmount())
                .pendingAmount(pending)
                .status(e.getStatus())
                .fundedAt(e.getFundedAt())
                .milestones(e.getMilestones().stream().map(EscrowMilestoneResponse::from).toList())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
