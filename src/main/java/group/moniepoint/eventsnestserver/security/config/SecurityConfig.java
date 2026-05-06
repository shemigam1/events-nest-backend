package group.moniepoint.eventsnestserver.security.config;

import group.moniepoint.eventsnestserver.security.filter.EventsNestAuthenticationFilter;
import group.moniepoint.eventsnestserver.security.filter.EventsNestAuthorizationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   EventsNestAuthenticationFilter authenticationFilter,
                                                   EventsNestAuthorizationFilter authorizationFilter)
            throws Exception {
        String[] authWhiteList = new String[]{"/api/v1/auth/login", "/api/v1/auth/register"};
        return http.addFilterAt(authenticationFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(authorizationFilter, EventsNestAuthenticationFilter.class)
                .authorizeHttpRequests(r ->
                        r.requestMatchers(POST, authWhiteList).permitAll())
                .authorizeHttpRequests(r ->
                        r.requestMatchers(GET, "/api/events", "/api/events/**").permitAll())
                .authorizeHttpRequests(r ->
                        r.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
