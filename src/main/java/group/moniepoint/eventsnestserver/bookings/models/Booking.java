package group.moniepoint.eventsnestserver.bookings.models;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.events.models.Events;
import group.moniepoint.eventsnestserver.tiers.models.TicketTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendee_id", nullable = false, updatable = false)
    private User attendee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Events event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false, updatable = false)
    private TicketTier tier;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PAID;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /**
     * Monnify's transactionReference returned from initialize-transaction.
     * UNIQUE — backs idempotency on the webhook + verify paths.
     * Null for legacy SIMULATED-* bookings predating M3.2.
     */
    @Column(name = "monnify_transaction_ref", length = 255, unique = true)
    private String monnifyTransactionRef;

    /** Set when the booking transitions PENDING → PAID. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * Optimistic-lock guard against concurrent finalize-payment calls.
     * See V12 migration for the race we're defending against.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
