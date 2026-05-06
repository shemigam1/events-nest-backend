package group.moniepoint.eventsnestserver.user;

import group.moniepoint.eventsnestserver.user.dto.RegisterUserRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.http.HttpStatus.CREATED;

@Controller
@RequestMapping("/api/v1/auth/register")
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> register(@RequestBody RegisterUserRequest registerUserRequest) throws Exception {
        return ResponseEntity.status(CREATED)
                .body(userService.register(registerUserRequest));
    }
}
