package group.moniepoint.eventsnestserver.email;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.UUID;

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
    public void sendCheckInStaffInvite(String toEmail,
                                       String staffName,
                                       String rawToken,
                                       String eventTitle,
                                       UUID eventId,
                                       String eventCode) {
        send(toEmail, "You've been invited as check-in staff for " + eventTitle,
                buildCheckInStaffEmail(staffName, rawToken, eventTitle, eventId, eventCode));
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

    private String buildCheckInStaffEmail(String staffName,
                                          String rawToken,
                                          String eventTitle,
                                          UUID eventId,
                                          String eventCode) {
        // Deep link pre-fills event code and token so staff lands on the
        // scanner ready to go. Frontend scrubs both from the URL bar via
        // history.replaceState so they don't leak via Referer or browser history.
        String deepLink = eventId == null
                ? frontendUrl + "/checkin"
                : frontendUrl + "/checkin?eventCode=" + (eventCode != null ? eventCode : eventId)
                  + "&eventId=" + eventId + "&token=" + rawToken;

        // Legacy fallback block: if eventId is null we omit the button and keep
        // the manual-token instructions only.
        String linkBlock = eventId == null ? "" : """
                <div style="text-align:center;margin:24px 0;">
                  <a href="%s" style="background-color:#3b4cca;color:white;padding:14px 28px;
                     text-decoration:none;border-radius:6px;font-size:16px;">Open check-in scanner</a>
                </div>
                <p style="color:#888;font-size:12px;text-align:center;margin:0 0 24px;">
                  Or enter the event code and token manually:
                </p>
                """.formatted(deepLink);

        // Show the short event code for manual entry — much easier to type than a UUID.
        // Falls back to the UUID if code is missing (e.g. legacy rows).
        String displayCode = (eventCode != null && !eventCode.isBlank()) ? eventCode
                : (eventId != null ? eventId.toString() : null);
        String eventIdBlock = displayCode == null ? "" : """
                <div style="background:#f8f9fc;border:1px solid #eee;border-radius:6px;padding:12px 16px;margin-bottom:12px;">
                  <div style="font-size:11px;color:#888;text-transform:uppercase;letter-spacing:0.06em;margin-bottom:4px;">Event Code</div>
                  <div style="font-family:monospace;font-size:16px;letter-spacing:0.08em;font-weight:600;">%s</div>
                </div>
                """.formatted(displayCode);

        return """
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px;">
                  <h2 style="color:#1a1a2e;">Check-In Staff Invitation</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>You have been invited as check-in staff for <strong>%s</strong>.</p>
                  %s
                  %s
                  <div style="background:#f4f4f4;border-left:4px solid #3b4cca;padding:12px 16px;
                              font-family:monospace;font-size:13px;word-break:break-all;">
                    <div style="font-size:11px;color:#888;text-transform:uppercase;letter-spacing:0.06em;margin-bottom:4px;font-family:Arial,sans-serif;">Staff token</div>
                    %s
                  </div>
                  <p style="color:#e53e3e;font-size:13px;margin-top:16px;">Keep this token private.</p>
                  <p style="color:#888;font-size:13px;">Expires 24 hours after the event ends.</p>
                  <hr style="border:none;border-top:1px solid #eee;margin-top:32px;"/>
                  <p style="color:#aaa;font-size:12px;">EventsNest Platform</p>
                </body></html>
                """.formatted(staffName, eventTitle, linkBlock, eventIdBlock, rawToken);
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
