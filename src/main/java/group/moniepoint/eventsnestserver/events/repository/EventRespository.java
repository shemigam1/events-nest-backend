package group.moniepoint.eventsnestserver.events.repository;

import group.moniepoint.eventsnestserver.events.models.Events;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRespository extends JpaRepository<Events, UUID> {


}
