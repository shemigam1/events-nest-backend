package group.moniepoint.eventsnestserver.admin.service;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminInitializer initializer;

    @BeforeEach
    void wireProperties() {
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@eventsnest.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-change-me");
        ReflectionTestUtils.setField(initializer, "adminFirstName", "Platform");
        ReflectionTestUtils.setField(initializer, "adminLastName", "Admin");
    }

    @Test
    void seedsAdminWhenNoneExists() {
        when(userRepository.existsByEmail("admin@eventsnest.com")).thenReturn(false);
        when(passwordEncoder.encode("admin-change-me")).thenReturn("encoded-hash");

        initializer.run();

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User saved = savedCaptor.getValue();

        assertThat(saved.getEmail()).isEqualTo("admin@eventsnest.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getFirstName()).isEqualTo("Platform");
        assertThat(saved.getLastName()).isEqualTo("Admin");
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void doesNotSeedWhenAdminAlreadyPresent() {
        when(userRepository.existsByEmail("admin@eventsnest.com")).thenReturn(true);

        initializer.run();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.any());
    }
}
