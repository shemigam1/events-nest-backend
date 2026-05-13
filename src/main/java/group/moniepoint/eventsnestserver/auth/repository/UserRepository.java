package group.moniepoint.eventsnestserver.auth.repository;

import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByVendorVerificationStatusOrderByVendorVerificationSubmittedAtAsc(
            VendorVerificationStatus status);
}
