package com.pos.service;

import com.pos.entity.Company;
import com.pos.util.SmtpPasswordEncryption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Builds a JavaMailSender from the company's stored SMTP settings (for sending receipts and verification).
 * When company has no SMTP config, returns null so callers can fall back to env-configured sender.
 */
@Slf4j
@Component
public class CompanyMailSenderFactory {

    @Value("${smtp.encryption.key:}")
    private String encryptionKey;

    /**
     * Creates a mail sender from the company's SMTP config, or null if not configured.
     * Auto-corrects the SMTP host when a personal Outlook/Hotmail/Live address is saved
     * against the Office 365 host — a common misconfiguration.
     */
    public JavaMailSender createSender(Company company) {
        if (company == null || company.getSmtpHost() == null || company.getSmtpHost().isBlank()) {
            return null;
        }
        String username = company.getSmtpUsername();
        String password = decryptPassword(company.getSmtpPasswordEncrypted());
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.debug("Company SMTP incomplete: missing username or password");
            return null;
        }

        // Auto-correct: personal @outlook/@hotmail/@live accounts must use smtp-mail.outlook.com,
        // not smtp.office365.com (which is for work/school accounts only).
        String host = resolveHost(company.getSmtpHost(), username);

        try {
            int port = company.getSmtpPort() != null ? company.getSmtpPort() : 587;

            // Gmail always requires STARTTLS on port 587 regardless of the saved toggle.
            // Port 465 uses implicit SSL (SMTPS) for any provider.
            boolean isGmail = host.toLowerCase().contains("smtp.gmail.com");
            boolean useSSL = port == 465;
            boolean startTls = useSSL ? false : (isGmail || Boolean.TRUE.equals(company.getSmtpStartTls()));

            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            sender.setUsername(username);
            sender.setPassword(password);

            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", startTls ? "true" : "false");
            props.put("mail.smtp.starttls.required", startTls ? "true" : "false");
            if (useSSL) {
                props.put("mail.smtp.ssl.enable", "true");
            }
            props.put("mail.smtp.ssl.trust", host);
            props.put("mail.smtp.connectiontimeout", "15000");
            props.put("mail.smtp.timeout", "15000");
            props.put("mail.smtp.writetimeout", "15000");
            sender.setJavaMailProperties(props);
            log.debug("Created mail sender: host={} port={} startTls={} ssl={} username={}",
                    host, port, startTls, useSSL, username);
            return sender;
        } catch (Exception e) {
            log.warn("Failed to create mail sender from company config: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns the correct SMTP host for the given username.
     * Personal Microsoft accounts (@outlook.com, @hotmail.com, @live.com) must use
     * smtp-mail.outlook.com, not smtp.office365.com.
     */
    private String resolveHost(String configuredHost, String username) {
        boolean isOffice365Host = "smtp.office365.com".equalsIgnoreCase(configuredHost.trim());
        boolean isPersonalAccount = username != null &&
                username.toLowerCase().matches(".*@(outlook|hotmail|live)\\..+");
        if (isOffice365Host && isPersonalAccount) {
            log.warn("Auto-correcting SMTP host: {} uses a personal Microsoft account but smtp.office365.com " +
                     "was configured — switching to smtp-mail.outlook.com", username);
            return "smtp-mail.outlook.com";
        }
        return configuredHost;
    }

    private String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isBlank() || encryptionKey == null || encryptionKey.isBlank()) {
            return null;
        }
        return SmtpPasswordEncryption.decrypt(encrypted, encryptionKey);
    }
}
