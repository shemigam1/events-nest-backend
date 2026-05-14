package group.moniepoint.eventsnestserver.contracts.dto.response;

import group.moniepoint.eventsnestserver.contracts.model.EscrowMilestone;
import group.moniepoint.eventsnestserver.contracts.model.MilestoneStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EscrowMilestoneResponse {

    private UUID id;
    private UUID escrowId;
    private String title;
    private String description;
    private BigDecimal amount;
    private MilestoneStatus status;
    private int displayOrder;
    private LocalDateTime approvedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;

    public static EscrowMilestoneResponse from(EscrowMilestone m) {
        return EscrowMilestoneResponse.builder()
                .id(m.getId())
                .escrowId(m.getEscrow().getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .amount(m.getAmount())
                .status(m.getStatus())
                .displayOrder(m.getDisplayOrder())
                .approvedAt(m.getApprovedAt())
                .releasedAt(m.getReleasedAt())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
