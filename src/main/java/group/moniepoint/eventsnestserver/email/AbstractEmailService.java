package group.moniepoint.eventsnestserver.email;

import group.moniepoint.eventsnestserver.calendar.CalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractEmailService implements EmailService {

    @Value("${app.frontend-url}")
    protected String frontendUrl;

    @Autowired
    private EmailTemplateLoader templateLoader;

    @Autowired
    private CalendarService calendarService;

    // ─── EmailService methods ────────────────────────────────────────────────────

    @Override
    public void sendAdminInvitation(String toEmail, String token) {
        String url = frontendUrl + "/admin/register?token=" + token;
        String html = templateLoader.render("admin-invite.html", Map.of(
                "registrationUrl", url
        ));
        send(toEmail, "You've been invited to join EventsNest as an Admin", html);
    }

    @Override
    public void sendCheckInStaffInvite(String toEmail,
                                       String staffName,
                                       String rawToken,
                                       String eventTitle,
                                       UUID eventId,
                                       String eventCode) {
        String html = templateLoader.render("staff-invite.html", Map.of(
                "staffName",    staffName != null ? staffName : "",
                "eventTitle",   eventTitle != null ? eventTitle : "",
                "rawToken",     rawToken != null ? rawToken : "",
                "linkBlock",    buildLinkBlock(eventId, eventCode, rawToken),
                "eventIdBlock", buildEventIdBlock(eventId, eventCode)
        ));
        send(toEmail, "You've been invited as check-in staff for " + eventTitle, html);
    }

    @Override
    public void sendBookingConfirmation(String toEmail, String attendeeName, String eventTitle,
                                        String tierName, Integer quantity, BigDecimal totalAmount,
                                        String paymentReference) {
        String name  = attendeeName == null || attendeeName.isBlank() ? "there" : attendeeName;
        String total = totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) == 0
                ? "Free" : "₦" + totalAmount.toPlainString();

        String html = templateLoader.render("booking-confirmation.html", Map.of(
                "attendeeName",     name,
                "eventTitle",       eventTitle != null ? eventTitle : "",
                "tierName",         tierName != null ? tierName : "",
                "quantity",         quantity != null ? quantity.toString() : "0",
                "totalAmount",      total,
                "paymentReference", paymentReference != null ? paymentReference : "—",
                "ticketsUrl",       frontendUrl + "/tickets",
                "googleCalendarUrl", "",
                "eventDate",        "",
                "eventLocation",    ""
        ));
        send(toEmail, "Your booking for " + eventTitle + " is confirmed", html);
    }

    @Override
    public void sendBookingConfirmationWithCalendar(String toEmail, String attendeeName, String eventTitle,
                                                    String tierName, Integer quantity, BigDecimal totalAmount,
                                                    String paymentReference, String googleCalendarUrl,
                                                    String eventDate, String eventLocation) {
        String name  = attendeeName == null || attendeeName.isBlank() ? "there" : attendeeName;
        String total = totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) == 0
                ? "Free" : "₦" + totalAmount.toPlainString();

        String calendarButtonHtml = googleCalendarUrl != null && !googleCalendarUrl.isBlank()
                ? String.format("""
                    <div style="text-align:center;margin:24px 0;">
                      <a href="%s" style="display:inline-block;background-color:#4285F4;color:white;
                         padding:14px 28px;text-decoration:none;border-radius:6px;font-size:16px;
                         font-weight:bold;">📅 Add to Google Calendar</a>
                    </div>
                    <p style="color:#666;font-size:12px;text-align:center;">
                      Alternatively, import the calendar file attached to this email into Outlook, Apple Calendar, or any calendar app.
                    </p>
                    """, googleCalendarUrl)
                : "";

        Map<String, String> templateVars = new LinkedHashMap<>();
        templateVars.put("attendeeName", name);
        templateVars.put("eventTitle", eventTitle != null ? eventTitle : "");
        templateVars.put("tierName", tierName != null ? tierName : "");
        templateVars.put("quantity", quantity != null ? quantity.toString() : "0");
        templateVars.put("totalAmount", total);
        templateVars.put("paymentReference", paymentReference != null ? paymentReference : "—");
        templateVars.put("ticketsUrl", frontendUrl + "/tickets");
        templateVars.put("googleCalendarUrl", googleCalendarUrl != null ? googleCalendarUrl : "");
        templateVars.put("eventDate", eventDate != null ? eventDate : "");
        templateVars.put("eventLocation", eventLocation != null ? eventLocation : "");
        templateVars.put("calendarButtonHtml", calendarButtonHtml);
        String html = templateLoader.render("booking-confirmation.html", templateVars);
        send(toEmail, "✓ " + eventTitle + " is on your calendar", html);
    }

    @Override
    public void sendEventApproved(String toEmail, String organiserName, String eventTitle) {
        String name = organiserName == null || organiserName.isBlank() ? "there" : organiserName;
        String html = templateLoader.render("event-approved.html", Map.of(
                "organiserName", name,
                "eventTitle",    eventTitle != null ? eventTitle : "",
                "frontendUrl",   frontendUrl
        ));
        send(toEmail, "Your event \"" + eventTitle + "\" has been approved", html);
    }

    @Override
    public void sendEventRejected(String toEmail, String organiserName, String eventTitle, String reason) {
        String name       = organiserName == null || organiserName.isBlank() ? "there" : organiserName;
        String safeReason = reason == null || reason.isBlank() ? "(no reason provided)" : reason;
        String html = templateLoader.render("event-rejected.html", Map.of(
                "organiserName", name,
                "eventTitle",    eventTitle != null ? eventTitle : "",
                "reason",        safeReason,
                "frontendUrl",   frontendUrl
        ));
        send(toEmail, "Your event \"" + eventTitle + "\" needs revision", html);
    }

    @Override
    public void sendGuestRsvpInvite(String toEmail, String guestName, String eventTitle,
                                    UUID eventId, String rawToken) {
        String acceptUrl  = frontendUrl + "/rsvp?token=" + rawToken + "&response=ACCEPTED";
        String declineUrl = frontendUrl + "/rsvp?token=" + rawToken + "&response=DECLINED";
        String html = templateLoader.render("guest-rsvp-invite.html", Map.of(
                "guestName",      guestName != null ? guestName : "there",
                "eventTitle",     eventTitle != null ? eventTitle : "",
                "rsvpAcceptUrl",  acceptUrl,
                "rsvpDeclineUrl", declineUrl,
                "rsvpToken",      rawToken != null ? rawToken : ""
        ));
        send(toEmail, "You're invited to " + eventTitle, html);
    }

    @Override
    public void sendBudgetAlert(String toEmail, String organiserName, String eventTitle,
                                UUID eventId, java.math.BigDecimal totalBudget,
                                java.math.BigDecimal totalActualSpend,
                                java.math.BigDecimal remainingBudget,
                                int spendPercent) {
        String name = organiserName == null || organiserName.isBlank() ? "there" : organiserName;
        String html = templateLoader.render("budget-alert.html", Map.of(
                "organiserName",    name,
                "eventTitle",       eventTitle != null ? eventTitle : "",
                "spendPercent",     String.valueOf(spendPercent),
                "totalBudget",      "₦" + totalBudget.toPlainString(),
                "totalActualSpend", "₦" + totalActualSpend.toPlainString(),
                "remainingBudget",  "₦" + remainingBudget.toPlainString(),
                "budgetUrl",        frontendUrl + "/organiser/events/" + eventId + "/budget"
        ));
        send(toEmail, "⚠ Budget Alert: " + spendPercent + "% spent on " + eventTitle, html);
    }

    @Override
    public void sendRatingRequest(String toEmail, String attendeeName, String eventTitle, UUID ratingFormId) {
        String name = attendeeName == null || attendeeName.isBlank() ? "there" : attendeeName;
        String html = templateLoader.render("rating-request.html", Map.of(
                "attendeeName", name,
                "eventTitle",   eventTitle != null ? eventTitle : "",
                "ratingUrl",    frontendUrl + "/rate/" + ratingFormId
        ));
        send(toEmail, "How was " + eventTitle + "? Leave a review", html);
    }

    @Override
    public void sendPasswordReset(String toEmail, String name, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        String html = templateLoader.render("password-reset.html", Map.of(
                "name",     name != null && !name.isBlank() ? name : "there",
                "resetUrl", resetUrl
        ));
        send(toEmail, "Reset your EventsNest password", html);
    }

    @Override
    public void sendVendorInquiry(String toEmail, String vendorName, String organizerName,
                                  String eventTitle, String message, String serviceType, String chatUrl) {
        String name = vendorName == null || vendorName.isBlank() ? "there" : vendorName;
        String serviceTypeBlock = (serviceType != null && !serviceType.isBlank())
                ? "<p><strong>Service type:</strong> " + serviceType + "</p>"
                : "";
        String html = templateLoader.render("vendor-inquiry.html", Map.of(
                "vendorName",       name,
                "organizerName",    organizerName != null ? organizerName : "",
                "eventTitle",       eventTitle != null ? eventTitle : "",
                "message",          message != null ? message : "",
                "serviceTypeBlock", serviceTypeBlock,
                "chatUrl",          chatUrl
        ));
        send(toEmail, organizerName + " sent you an inquiry for " + eventTitle, html);
    }

    @Override
    public void sendVendorContractOffer(String toEmail, String vendorName, String organizerName,
                                        String eventTitle, String contractTitle,
                                        java.math.BigDecimal amount, String contractUrl) {
        String name = vendorName == null || vendorName.isBlank() ? "there" : vendorName;
        String html = templateLoader.render("vendor-contract-offer.html", Map.of(
                "vendorName",    name,
                "organizerName", organizerName != null ? organizerName : "",
                "eventTitle",    eventTitle != null ? eventTitle : "",
                "contractTitle", contractTitle != null ? contractTitle : "",
                "amount",        amount != null ? "₦" + amount.toPlainString() : "—",
                "contractUrl",   contractUrl
        ));
        send(toEmail, "Contract offer from " + organizerName + " for " + eventTitle, html);
    }

    // ─── abstract — each provider implements this ────────────────────────────────

    protected abstract void send(String toEmail, String subject, String htmlBody);

    // ─── private helpers ─────────────────────────────────────────────────────────

    /**
     * Builds the CTA button block pointing to the check-in scanner deep link.
     * Returns an empty string when eventId is null (legacy / manual-only rows).
     */
    private String buildLinkBlock(UUID eventId, String eventCode, String rawToken) {
        if (eventId == null) return "";
        String displayCode = (eventCode != null && !eventCode.isBlank()) ? eventCode : eventId.toString();
        String deepLink = frontendUrl + "/checkin?eventCode=" + displayCode
                + "&eventId=" + eventId + "&token=" + rawToken;
        return """
                <div style="text-align:center;margin:24px 0;">
                  <a href="%s" style="background-color:#3b4cca;color:white;padding:14px 28px;
                     text-decoration:none;border-radius:6px;font-size:16px;">Open check-in scanner</a>
                </div>
                <p style="color:#888;font-size:12px;text-align:center;margin:0 0 24px;">
                  Or enter the event code and token manually:
                </p>
                """.formatted(deepLink);
    }

    /**
     * Builds the event-code display block shown beneath the CTA button.
     * Returns an empty string when neither eventId nor eventCode is available.
     */
    private String buildEventIdBlock(UUID eventId, String eventCode) {
        String displayCode = (eventCode != null && !eventCode.isBlank()) ? eventCode
                : (eventId != null ? eventId.toString() : null);
        if (displayCode == null) return "";
        return """
                <div style="background:#f8f9fc;border:1px solid #eee;border-radius:6px;padding:12px 16px;margin-bottom:12px;">
                  <div style="font-size:11px;color:#888;text-transform:uppercase;letter-spacing:0.06em;margin-bottom:4px;">Event Code</div>
                  <div style="font-family:monospace;font-size:16px;letter-spacing:0.08em;font-weight:600;">%s</div>
                </div>
                """.formatted(displayCode);
    }
}
