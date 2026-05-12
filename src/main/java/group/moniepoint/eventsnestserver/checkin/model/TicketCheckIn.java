package group.moniepoint.eventsnestserver.checkin.model;

import group.moniepoint.eventsnestserver.events.models.EventDay;
import group.moniepoint.eventsnestserver.tickets.models.Ticket;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "ticket_check_ins",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ticket_check_ins_ticket_day",
                columnNames = {"ticket_id", "event_day_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCheckIn {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_day_id", nullable = false)
    private EventDay eventDay;

    @Column(name = "checked_in_at", nullable = false)
    private LocalDateTime checkedInAt;

    @Column(name = "checked_in_by_label", length = 100)
    private String checkedInByLabel;
}
