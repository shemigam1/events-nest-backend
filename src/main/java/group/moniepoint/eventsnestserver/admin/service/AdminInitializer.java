package group.moniepoint.eventsnestserver.admin.service;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default ADMIN account on startup if one does not already exist.
 * Credentials are read from {@code admin.email} and {@code admin.password}
 * (set via application.properties or environment variables) — admin
 * registration is intentionally not exposed via any HTTP endpoint per
 * PRD §8.4 to prevent privilege escalation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.first-name:Platform}")
    private String adminFirstName;

    @Value("${admin.last-name:Admin}")
    private String adminLastName;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Default admin already present, skipping seed: {}", adminEmail);
            return;
        }

        User admin = User.builder()
                .firstName(adminFirstName)
                .lastName(adminLastName)
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(admin);

        log.info("Seeded default admin: {}", adminEmail);
    }
}
