package group.moniepoint.eventsnestserver.ratings.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "rating_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingQuestion {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false, updatable = false)
    private RatingForm form;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 30)
    private QuestionType questionType;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = true;

    /** Comma-separated choices for MULTIPLE_CHOICE questions. */
    @Column(columnDefinition = "TEXT")
    private String options;
}
