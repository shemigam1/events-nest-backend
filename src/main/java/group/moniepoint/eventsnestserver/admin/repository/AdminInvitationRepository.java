package group.moniepoint.eventsnestserver.admin.repository;

import group.moniepoint.eventsnestserver.admin.model.AdminInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, UUID> {
    Optional<AdminInvitation> findByToken(String token);
    Optional<AdminInvitation> findByEmail(String email);
}
