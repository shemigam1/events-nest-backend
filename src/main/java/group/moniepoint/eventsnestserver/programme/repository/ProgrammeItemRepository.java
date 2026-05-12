package group.moniepoint.eventsnestserver.programme.repository;

import group.moniepoint.eventsnestserver.programme.model.ProgrammeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgrammeItemRepository extends JpaRepository<ProgrammeItem, UUID> {

    List<ProgrammeItem> findAllByEventIdOrderByDisplayOrderAsc(UUID eventId);
}
