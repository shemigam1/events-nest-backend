package group.moniepoint.eventsnestserver.security.service;

import group.moniepoint.eventsnestserver.user.dto.UserResponse;
import group.moniepoint.eventsnestserver.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class EventsNestUserDetailsService implements UserDetailsService {
    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserResponse userResponse = userService.getUserBy(email);
        List<? extends GrantedAuthority> authorities = userResponse.getAuthorities()
                .stream()
                .map(SimpleGrantedAuthority::new).toList();
        return new User(userResponse.getEmail(), userResponse.getPassword(), authorities);
    }
}
