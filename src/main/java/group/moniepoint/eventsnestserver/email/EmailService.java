package group.moniepoint.eventsnestserver.email;

public interface EmailService {
    void sendAdminInvitation(String toEmail, String token);
}
