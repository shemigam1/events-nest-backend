package group.moniepoint.eventsnestserver.chat.model;

import group.moniepoint.eventsnestserver.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime joinedAt;

    /** Set to NOW() when the user calls markRead. Null = never explicitly read. */
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;
}
