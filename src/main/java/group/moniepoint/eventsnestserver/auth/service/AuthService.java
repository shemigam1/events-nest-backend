package group.moniepoint.eventsnestserver.auth.service;

import group.moniepoint.eventsnestserver.auth.dto.ForgotPasswordRequest;
import group.moniepoint.eventsnestserver.auth.dto.LoginRequest;
import group.moniepoint.eventsnestserver.auth.dto.LoginResponse;
import group.moniepoint.eventsnestserver.auth.dto.RegisterRequest;
import group.moniepoint.eventsnestserver.auth.dto.RegisterResponse;
import group.moniepoint.eventsnestserver.auth.dto.ResetPasswordRequest;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.dto.response.EventsNestResponse;

public interface AuthService {
    EventsNestResponse<RegisterResponse> register(RegisterRequest request);
    EventsNestResponse<LoginResponse> login(LoginRequest request);
    EventsNestResponse<LoginResponse> refresh(String refreshToken);
    User findByEmail(String email);
    EventsNestResponse<Void> forgotPassword(ForgotPasswordRequest request);
    EventsNestResponse<Void> resetPassword(ResetPasswordRequest request);
}
