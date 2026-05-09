package group.moniepoint.eventsnestserver.email;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;

public abstract class AbstractEmailService implements EmailService {

    @Value("${app.frontend-url}")
    protected String frontendUrl;

    // ─── EmailService methods — delegate to send() ───────────────────────────────

    @Override
    public void sendAdminInvitation(String toEmail, String token) {
        String url = frontendUrl + "/admin/register?token=" + token;
        send(toEmail, "You've been invited to join EventsNest as an Admin",
                buildInvitationEmail(url));
    }

    @Override
    public void sendCheckInStaffInvite(String toEmail, String staffName, String rawToken, String eventTitle) {
        send(toEmail, "You've been invited as check-in staff for " + eventTitle,
                buildCheckInStaffEmail(staffName, rawToken, eventTitle));
    }

    @Override
    public void sendBookingConfirmation(String toEmail, String attendeeName, String eventTitle,
                                        String tierName, Integer quantity, BigDecimal totalAmount,
                                        String paymentReference) {
        send(toEmail, "Your booking for " + eventTitle + " is confirmed",
                buildBookingConfirmationEmail(attendeeName, eventTitle, tierName,
                        quantity, totalAmount, paymentReference));
    }

    @Override
    public void sendEventApproved(String toEmail, String organiserName, String eventTitle) {
        send(toEmail, "Your event \"" + eventTitle + "\" has been approved",
                buildEventApprovedEmail(organiserName, eventTitle));
    }

    @Override
    public void sendEventRejected(String toEmail, String organiserName, String eventTitle, String reason) {
        send(toEmail, "Your event \"" + eventTitle + "\" needs revision",
                buildEventRejectedEmail(organiserName, eventTitle, reason));
    }

    // ─── abstract — each provider implements this ────────────────────────────────

    protected abstract void send(String toEmail, String subject, String htmlBody);

    // ─── templates ───────────────────────────────────────────────────────────────

    private String buildInvitationEmail(String registrationUrl) {
        return """
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px;">
                  <h2 style="color:#1a1a2e;">You're invited to EventsNest</h2>
                  <p>An admin has invited you to join <strong>EventsNest</strong> as a platform administrator.</p>
                  <div style="text-align:center;margin:32px 0;">
                    <a href="%s" style="background-color:#3b4cca;color:white;padding:14px 28px;
                       text-decoration:none;border-radius:6px;font-size:16px;">Set Up My Admin Account</a>
                  </div>
                  <p style="color:#888;font-size:13px;">This link expires in <strong>7 days</strong>.</p>
                  <hr style="border:none;border-top:1px solid #eee;margin-top:32px;"/>
                  <p style="color:#aaa;font-size:12px;">EventsNest Platform</p>
                </body></html>
                """.formatted(registrationUrl);
    }

    private String buildCheckInStaffEmail(String staffName, String rawToken, String eventTitle) {
        return """
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px;">
                  <h2 style="color:#1a1a2e;">Check-In Staff Invitation</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>You have been invited as check-in staff for <strong>%s</strong>.</p>
                  <div style="background:#f4f4f4;border-left:4px solid #3b4cca;padding:16px;margin:24px 0;
                              font-family:monospace;font-size:16px;word-break:break-all;">%s</div>
                  <p style="color:#e53e3e;font-size:13px;">Keep this token private.</p>
                  <p style="color:#888;font-size:13px;">Expires 24 hours after the event ends.</p>
                  <hr style="border:none;border-top:1px solid #eee;margin-top:32px;"/>
                  <p style="color:#aaa;font-size:12px;">EventsNest Platform</p>
                </body></html>
                """.formatted(staffName, eventTitle, rawToken);
    }

    private String buildBookingConfirmationEmail(String attendeeName, String eventTitle, String tierName,
                                                  Integer quantity, BigDecimal totalAmount,
                                                  String paymentReference) {
        String name = attendeeName == null || attendeeName.isBlank() ? "there" : attendeeName;
        String total = totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) == 0
                ? "Free" : "₦" + totalAmount.toPlainString();
        String ref = paymentReference == null ? "—" : paymentReference;
        String ticketsUrl = frontendUrl + "/tickets";
        return """
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px;">
                  <h2 style="color:#1a1a2e;">Booking confirmed</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Your booking for <strong>%s</strong> is confirmed.</p>
                  <table cellpadding="0" cellspacing="0" border="0"
                         style="margin:24px 0;width:100%%;border:1px solid #eee;border-radius:8px;">
                    <tr style="background:#f8f9fc;">
                      <td style="padding:12px 16px;font-size:13px;color:#555;width:40%%;">Tier</td>
                      <td style="padding:12px 16px;font-size:13px;font-weight:600;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:12px 16px;font-size:13px;color:#555;border-top:1px solid #eee;">Quantity</td>
                      <td style="padding:12px 16px;font-size:13px;font-weight:600;border-top:1px solid #eee;">%d ticket(s)</td>
                    </tr>
                    <tr style="background:#f8f9fc;">
                      <td style="padding:12px 16px;font-size:13px;color:#555;border-top:1px solid #eee;">Total</td>
                      <td style="padding:12px 16px;font-size:13px;font-weight:600;border-top:1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:12px 16px;font-size:13px;color:#555;border-top:1px solid #eee;">Reference</td>
                      <td style="padding:12px 16px;font-size:12px;font-family:monospace;border-top:1px solid #eee;">%s</td>
                    </tr>
                  </table>
                  <div style="text-align:center;margin:32px 0;">
                    <a href="%s" style="background-color:#3b4cca;color:white;padding:14px 28px;
                       text-decoration:none;border-radius:6px;font-size:16px;">View my tickets</a>
                  </div>
                  <hr style="border:none;border-top:1px solid #eee;margin-top:32px;"/>
                  <p style="color:#aaa;font-size:12px;">EventsNest Platform</p>
                </body></html>
                """.formatted(name, eventTitle, tierName, quantity, total, ref, ticketsUrl);
    }

    private String buildEventApprovedEmail(String organiserName, String eventTitle) {
        String name = organiserName == null || organiserName.isBlank() ? "there" : organiserName;
        return """
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px;">
                  <h2 style="color:#0F9D58;">Your event is live!</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p><strong>%s</strong> has been approved and published.</p>
                  <div style="text-align:center;margin:32px 0;">
                    <a href="%s/organiser" style="background-color:#3b4cca;color:white;padding:14px 28px;
                       text-decoration:none;border-radius:6px;font-size:16px;">Open organiser console</a>
                  </div>
                  <hr style="border:none;border-top:1px solid #eee;margin-top:32px;"/>
                  <p style="color:#aaa;font-size:12px;">EventsNest Platform</p>
                </body></html>
                """.formatted(name, eventTitle, frontendUrl);
    }

    private String buildEventRejectedEmail(String organiserName, String eventTitle, String reason) {
        String name = organiserName == null || organiserName.isBlank() ? "there" : organiserName;
        String safeReason = reason == null || reason.isBlank() ? "(no reason provided)" : reason;
        return """
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px;">
                  <h2 style="color:#B8770A;">Your event needs revision</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p><strong>%s</strong> was returned to draft with the following note:</p>
                  <div style="background:#fff7e6;border-left:4px solid #B8770A;padding:16px;margin:24px 0;font-size:14px;">%s</div>
                  <div style="text-align:center;margin:32px 0;">
                    <a href="%s/organiser" style="background-color:#3b4cca;color:white;padding:14px 28px;
                       text-decoration:none;border-radius:6px;font-size:16px;">Edit event</a>
                  </div>
                  <hr style="border:none;border-top:1px solid #eee;margin-top:32px;"/>
                  <p style="color:#aaa;font-size:12px;">EventsNest Platform</p>
                </body></html>
                """.formatted(name, eventTitle, safeReason, frontendUrl);
    }
}
