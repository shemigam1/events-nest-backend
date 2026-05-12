package group.moniepoint.eventsnestserver.security.config;

import group.moniepoint.eventsnestserver.security.filter.AuthorizationFilter;
import group.moniepoint.eventsnestserver.security.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
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
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(authorizationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/admin/invite/complete").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/events/*/checkin").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/rsvp").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/ratings/*/respond").permitAll()
                        // Monnify posts here with no JWT — authenticity is enforced
                        // by HMAC signature inside PaymentController.
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/payments/monnify/webhook").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/events", "/api/v1/events/*",
                                "/api/v1/events/*/tiers",
                                "/api/v1/events/*/days",
                                "/api/v1/events/code/*",
                                "/api/v1/events/*/programme",
                                "/api/v1/events/*/ratings/form").permitAll()
                        // Operational endpoints. /actuator/health (plus the
                        // liveness/readiness sub-probes) is always public so
                        // Docker / k8s healthchecks can hit it without auth.
                        // /actuator/prometheus is open at the network level
                        // because in compose only sibling services (Prometheus)
                        // can reach the app port; in real prod this would be
                        // bound to a separate management port and ACL'd.
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        // WebSocket handshake endpoint — auth is handled inside
                        // JwtChannelInterceptor on the STOMP CONNECT frame.
                        .requestMatchers("/ws/**").permitAll()
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
