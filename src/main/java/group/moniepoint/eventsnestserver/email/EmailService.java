package group.moniepoint.eventsnestserver.email;

public interface EmailService {
    void sendAdminInvitation(String toEmail, String token);
    void sendCheckInStaffInvite(String toEmail, String staffName, String rawToken, String eventTitle);
}
