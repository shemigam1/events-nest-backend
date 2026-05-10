package group.moniepoint.eventsnestserver.security.config;

import group.moniepoint.eventsnestserver.security.filter.AuthorizationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthorizationFilter authorizationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authorizationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/invite/complete").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/events/*/checkin").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/events", "/api/v1/events/*",
                                "/api/v1/events/*/tiers").permitAll()
                        // Operational endpoints. /actuator/health (plus the
                        // liveness/readiness sub-probes) is always public so
                        // Docker / k8s healthchecks can hit it without auth.
                        // /actuator/prometheus is open at the network level
                        // because in compose only sibling services (Prometheus)
                        // can reach the app port; in real prod this would be
                        // bound to a separate management port and ACL'd.
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.modelmapper.ModelMapper modelMapper() {
        org.modelmapper.ModelMapper mapper = new org.modelmapper.ModelMapper();
        mapper.typeMap(
                group.moniepoint.eventsnestserver.events.models.Events.class,
                group.moniepoint.eventsnestserver.events.dto.response.EventResponse.class)
            .addMappings(m -> m.skip(
                group.moniepoint.eventsnestserver.events.dto.response.EventResponse::setCreatedBy));
        return mapper;
    }

    /**
     * CORS allow-list. Origins are read from the {@code APP_CORS_ALLOWED_ORIGINS}
     * env var (comma-separated) so the same image can be deployed to multiple
     * environments without rebuilding. Default is local-dev only.
     *
     * <p>Patterns (not exact strings) are used so wildcards like
     * {@code https://eventsnest-*.vercel.app} match Vercel preview deploys
     * (each PR gets its own subdomain).
     *
     * <p>Spec note: {@code AllowCredentials=true} requires non-wildcard
     * origins, which is why we set {@code AllowedOriginPatterns} explicitly
     * with the configured list rather than the prior {@code "*"}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
