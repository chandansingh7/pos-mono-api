package com.pos.service;

import com.pos.entity.Company;
import com.pos.entity.Order;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.util.SmtpPasswordEncryption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Sends order receipt emails from the company's email (Settings) to the customer.
 * Requires SMTP to be configured (MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, etc.).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final CompanyMailSenderFactory companyMailSenderFactory;
    private final MicrosoftOAuthService microsoftOAuthService;
    private final MicrosoftGraphMailService microsoftGraphMailService;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${smtp.encryption.key:}")
    private String encryptionKey;

    public void sendReceipt(Company company, String toEmail, Order order) {
        String from = company.getEmail();
        if (from == null || from.isBlank()) {
            log.warn("[EM001] Company email not set");
            throw new BadRequestException(ErrorCode.EM001);
        }

        // Microsoft Graph flow (optional)
        if ("MICROSOFT".equalsIgnoreCase(company.getEmailSendMethod()) && company.getMsRefreshTokenEncrypted() != null && !company.getMsRefreshTokenEncrypted().isBlank()) {
            sendReceiptViaMicrosoft(company, toEmail, order, from);
            return;
        }

        JavaMailSender sender = companyMailSenderFactory.createSender(company);
        if (sender == null) {
            JavaMailSender fallback = mailSenderProvider.getIfAvailable();
            if (fallback == null || mailHost == null || mailHost.isBlank()) {
                log.warn("[EM002] SMTP not configured");
                throw new BadRequestException(ErrorCode.EM002);
            }
            sender = fallback;
        }

        String subject = "Receipt #" + order.getId() + " - " + (company.getName() != null ? company.getName() : "Your purchase");
        String htmlBody = buildReceiptHtml(company, order);

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            sender.send(message);
            log.info("Receipt email sent for order {} to {}", order.getId(), toEmail);
        } catch (MailAuthenticationException e) {
            String authMsg = fullMessage(e);
            log.warn("SMTP authentication failed for order {} (host={} user={}): {}",
                    order.getId(), company.getSmtpHost(), company.getSmtpUsername(), authMsg);
            if (isMicrosoftBasicAuthDisabled(authMsg, company.getSmtpUsername())) {
                throw new BadRequestException(ErrorCode.EM007);
            }
            if (isGmailAppPasswordRequired(authMsg)) {
                throw new BadRequestException(ErrorCode.EM008);
            }
            throw new BadRequestException(ErrorCode.EM006);
        } catch (MailException e) {
            log.error("Failed to send receipt email for order {}: {}", order.getId(), e.getMessage());
            throw new BadRequestException(ErrorCode.EM002, e.getMessage());
        } catch (MessagingException e) {
            log.error("Failed to send receipt email (messaging) for order {}: {}", order.getId(), e.getMessage());
            throw new BadRequestException(ErrorCode.EM002, e.getMessage());
        }
    }

    private void sendReceiptViaMicrosoft(Company company, String toEmail, Order order, String from) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("sendReceiptViaMicrosoft: SMTP_ENCRYPTION_KEY not configured; cannot decrypt Microsoft refresh token (companyId={})", company.getId());
            throw new BadRequestException(ErrorCode.EM002);
        }
        String refresh = SmtpPasswordEncryption.decrypt(company.getMsRefreshTokenEncrypted(), encryptionKey);
        if (refresh == null || refresh.isBlank()) {
            log.warn("sendReceiptViaMicrosoft: decrypted Microsoft refresh token is empty (companyId={})", company.getId());
            throw new BadRequestException(ErrorCode.EM002);
        }
        try {
            MicrosoftOAuthService.TokenResponse tok = microsoftOAuthService.refreshAccessToken(refresh);
            String subject = "Receipt #" + order.getId() + " - " + (company.getName() != null ? company.getName() : "Your purchase");
            String body = buildReceiptHtml(company, order);
            log.info("sendReceiptViaMicrosoft: attempting Graph sendMail from={} to={} orderId={}", from, toEmail, order.getId());
            microsoftGraphMailService.sendMail(tok.accessToken(), from, toEmail, subject, stripHtml(body));
            log.info("Receipt email (Microsoft) sent for order {} to {}", order.getId(), toEmail);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : "unknown error";
            log.warn("Failed to send receipt via Microsoft for order {} to {}: {}", order.getId(), toEmail, detail);
            // Include the Graph error detail in the response so admins can act on it
            throw new BadRequestException(ErrorCode.EM004, detail);
        }
    }

    /** Walks the full cause chain and concatenates all messages. */
    private static String fullMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append(" ");
            t = t.getCause();
        }
        return sb.toString().trim();
    }

    /**
     * Returns true when Microsoft has rejected the connection due to basic auth being
     * permanently disabled (error 535 5.7.139), OR when the username is a personal
     * Outlook/Hotmail/Live address (these accounts can never use basic SMTP auth).
     */
    private static boolean isMicrosoftBasicAuthDisabled(String fullMsg, String username) {
        if (fullMsg.contains("5.7.139") || fullMsg.contains("basic authentication is disabled")) {
            return true;
        }
        return username != null && username.toLowerCase().matches(".*@(outlook|hotmail|live)\\..+");
    }

    /** Returns true when Gmail requires an App Password (534 5.7.9). */
    private static boolean isGmailAppPasswordRequired(String fullMsg) {
        return fullMsg.contains("5.7.9") || fullMsg.contains("Application-specific password required");
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private String buildReceiptHtml(Company company, Order order) {
        String currency = company.getDisplayCurrency() != null ? company.getDisplayCurrency() : "USD";
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US);
        String dateStr = order.getCreatedAt() != null ? order.getCreatedAt().format(dateFmt) : "";

        StringBuilder rows = new StringBuilder();
        order.getItems().forEach(item -> {
            String name = item.getProduct() != null ? escapeHtml(item.getProduct().getName()) : "Item";
            String qty = item.getQuantity() != null ? item.getQuantity().stripTrailingZeros().toPlainString() : "0";
            String subtotal = formatMoney(item.getSubtotal(), currency);
            rows.append("<tr><td>").append(name).append("</td><td>").append(qty).append("</td><td style=\"text-align:right\">").append(subtotal).append("</td></tr>");
        });

        String companyName = escapeHtml(company.getName() != null ? company.getName() : "Receipt");
        String header = company.getReceiptHeaderText() != null ? "<p style=\"text-align:center;color:#555;font-size:12px\">" + escapeHtml(company.getReceiptHeaderText()) + "</p>" : "";
        String footer = company.getReceiptFooterText() != null ? "<p style=\"text-align:center;margin-top:16px;font-size:12px;color:#555\">" + escapeHtml(company.getReceiptFooterText()) + "</p>" : "";
        String address = company.getAddress() != null && !company.getAddress().isBlank() ? "<p style=\"text-align:center;color:#555;font-size:12px\">" + escapeHtml(company.getAddress()) + "</p>" : "";
        String phone = company.getPhone() != null && !company.getPhone().isBlank() ? "<p style=\"text-align:center;color:#555;font-size:12px\">" + escapeHtml(company.getPhone()) + "</p>" : "";

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body style=\"font-family:system-ui,sans-serif;max-width:400px;margin:0 auto;padding:16px;color:#222\">"
                + "<h2 style=\"text-align:center;margin-bottom:4px\">" + companyName + "</h2>"
                + address + phone + header
                + "<hr style=\"border:none;border-top:1px dashed #ccc;margin:12px 0\"/>"
                + "<p><strong>Order #" + order.getId() + "</strong> &nbsp; " + dateStr + "</p>"
                + "<table style=\"width:100%;border-collapse:collapse\"><thead><tr><th style=\"text-align:left;border-bottom:1px solid #ddd\">Item</th><th style=\"text-align:center;border-bottom:1px solid #ddd\">Qty</th><th style=\"text-align:right;border-bottom:1px solid #ddd\">Amount</th></tr></thead><tbody>"
                + rows
                + "</tbody></table>"
                + "<p style=\"text-align:right;margin-top:8px\"><strong>Subtotal</strong> " + formatMoney(order.getSubtotal(), currency) + "</p>"
                + "<p style=\"text-align:right\"><strong>Tax</strong> " + formatMoney(order.getTax(), currency) + "</p>"
                + (order.getDiscount() != null && order.getDiscount().compareTo(BigDecimal.ZERO) > 0 ? "<p style=\"text-align:right\"><strong>Discount</strong> -" + formatMoney(order.getDiscount(), currency) + "</p>" : "")
                + "<p style=\"text-align:right;font-size:1.1em;margin-top:8px;padding-top:8px;border-top:1px dashed #000\"><strong>TOTAL</strong> " + formatMoney(order.getTotal(), currency) + "</p>"
                + "<p style=\"text-align:right;color:#555;font-size:12px\">Payment: " + (order.getPaymentMethod() != null ? order.getPaymentMethod().toString().replace("_", " ") : "") + "</p>"
                + footer
                + "</body></html>";
    }

    private static String formatMoney(BigDecimal amount, String currencyCode) {
        if (amount == null) return "0.00";
        return currencyCode + " " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
