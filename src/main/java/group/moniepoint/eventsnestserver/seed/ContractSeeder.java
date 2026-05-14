package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.chat.model.Conversation;
import group.moniepoint.eventsnestserver.contracts.model.*;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowAccountRepository;
import group.moniepoint.eventsnestserver.contracts.repository.EscrowMilestoneRepository;
import group.moniepoint.eventsnestserver.contracts.repository.VendorContractRepository;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class ContractSeeder {

    private final VendorContractRepository  vendorContractRepository;
    private final EscrowAccountRepository   escrowAccountRepository;
    private final EscrowMilestoneRepository escrowMilestoneRepository;

    void seed(List<VendorApplication> vendorApps, Map<VendorApplication, Conversation> conversations) {
        int count = 0;
        for (VendorApplication app : vendorApps) {
            if (app.getStatus() != VendorApplicationStatus.ACCEPTED) continue;

            VendorContract contract = vendorContractRepository.save(VendorContract.builder()
                    .event(app.getEvent())
                    .organizer(app.getEvent().getCreatedBy())
                    .vendor(app.getApplicant())
                    .vendorApplication(app)
                    .conversation(conversations.get(app))
                    .title(app.getServiceType() + " Services - " + app.getEvent().getTitle())
                    .description("Contract for providing " + app.getServiceType()
                            + " services for event: " + app.getEvent().getTitle())
                    .terms("Payment terms: 50% upfront, 50% on completion. Service quality as per industry standards.")
                    .amount(app.getProposedAmount())
                    .status(ContractStatus.SIGNED)
                    .signedAt(LocalDateTime.now().minusDays(5))
                    .fundedAt(LocalDateTime.now().minusDays(3))
                    .activatedAt(LocalDateTime.now().minusDays(2))
                    .build());

            seedEscrowWithMilestones(contract, app.getProposedAmount());
            count++;
            log.debug("Seeded contract with escrow & milestones: {} (amount: ₦{})",
                    app.getServiceType(), app.getProposedAmount());
        }
        log.info("Seeded {} contracts with escrow accounts and milestones", count);
    }

    private void seedEscrowWithMilestones(VendorContract contract, BigDecimal amount) {
        int numMilestones = 3;
        BigDecimal perMilestone = amount.divide(new BigDecimal(numMilestones));

        EscrowAccount escrow = escrowAccountRepository.save(EscrowAccount.builder()
                .contract(contract)
                .totalAmount(amount)
                .releasedAmount(perMilestone)   // first milestone released
                .status(EscrowStatus.FUNDED)
                .fundedAt(LocalDateTime.now().minusDays(3))
                .build());

        List<EscrowMilestone> milestones = new ArrayList<>();
        for (int i = 1; i <= numMilestones; i++) {
            milestones.add(escrowMilestoneRepository.save(EscrowMilestone.builder()
                    .escrow(escrow)
                    .title("Stage " + i + ": " + milestoneTitle(i))
                    .description(milestoneDescription(i))
                    .amount(perMilestone)
                    .status(i == 1 ? MilestoneStatus.RELEASED
                                   : (i == 2 ? MilestoneStatus.APPROVED : MilestoneStatus.PENDING))
                    .displayOrder(i)
                    .approvedAt(i <= 2 ? LocalDateTime.now().minusDays(5 - i) : null)
                    .releasedAt(i == 1 ? LocalDateTime.now().minusDays(2) : null)
                    .build()));
        }

        escrow.setMilestones(milestones);
        escrowAccountRepository.save(escrow);
    }

    private static String milestoneTitle(int index) {
        return switch (index) {
            case 1 -> "Initial Deposit (Released)";
            case 2 -> "Partial Progress (Approved)";
            default -> "Final Delivery (Pending)";
        };
    }

    private static String milestoneDescription(int index) {
        return switch (index) {
            case 1 -> "Initial payment released to kickstart project";
            case 2 -> "Payment approved upon significant progress";
            default -> "Final payment pending upon project completion";
        };
    }
}
