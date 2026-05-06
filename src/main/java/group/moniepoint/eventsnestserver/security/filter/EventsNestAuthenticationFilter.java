package group.moniepoint.eventsnestserver.security.filter;

import group.moniepoint.eventsnestserver.security.dto.request.LoginRequest;
import group.moniepoint.eventsnestserver.security.service.JWTServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@Component
@AllArgsConstructor
public class EventsNestAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final AuthenticationManager authenticationManager;
    private final JWTServiceImpl jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        boolean isAuthRequest = request.getMethod().equals(POST.name())
                && request.getServletPath().equals("/api/v1/auth/login");
        if (isAuthRequest) {
            InputStream inputStream = request.getInputStream();
            LoginRequest loginRequest = objectMapper.readValue(inputStream, LoginRequest.class);
            String email = loginRequest.getEmail();
            String password = loginRequest.getPassword();

            Authentication authentication = new UsernamePasswordAuthenticationToken(email, password);
            Authentication authenticationResult = authenticationManager.authenticate(authentication);

            String accessToken = jwtService.generateAccessToken(authenticationResult);

            Map<String, String> loginResponse = buildAuthResponseWith(accessToken);

            response.getOutputStream().write(objectMapper.writeValueAsBytes(loginResponse));
            response.setContentType(APPLICATION_JSON_VALUE);
            response.flushBuffer();
        }
        filterChain.doFilter(request, response);
    }

    private static Map<String, String> buildAuthResponseWith(String token) {
        Map<String, String> loginResponse = new HashMap<>();
        loginResponse.put("access_token", token);
        loginResponse.put("token_type", "Bearer");
        return loginResponse;
    }
}
