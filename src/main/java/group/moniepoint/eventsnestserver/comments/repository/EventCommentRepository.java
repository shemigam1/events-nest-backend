package group.moniepoint.eventsnestserver.comments.repository;

import group.moniepoint.eventsnestserver.comments.model.EventComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventCommentRepository extends JpaRepository<EventComment, UUID> {

    /** Top-level comments for an event, newest first. Replies are loaded lazily by parent. */
    @Query("""
           SELECT c FROM EventComment c
           WHERE c.event.id = :eventId
             AND c.parent IS NULL
           ORDER BY c.createdAt DESC
           """)
    Page<EventComment> findTopLevelByEvent(@Param("eventId") UUID eventId, Pageable pageable);

    /** Replies under a parent comment, oldest first. */
    @Query("""
           SELECT c FROM EventComment c
           WHERE c.parent.id = :parentId
           ORDER BY c.createdAt ASC
           """)
    List<EventComment> findRepliesByParent(@Param("parentId") UUID parentId);

    long countByParentId(UUID parentId);
}
