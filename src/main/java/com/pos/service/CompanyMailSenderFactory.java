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
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(company.getSmtpHost());
            sender.setPort(company.getSmtpPort() != null ? company.getSmtpPort() : 587);
            sender.setUsername(username);
            sender.setPassword(password);
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", Boolean.TRUE.equals(company.getSmtpStartTls()) ? "true" : "false");
            sender.setJavaMailProperties(props);
            return sender;
        } catch (Exception e) {
            log.warn("Failed to create mail sender from company config: {}", e.getMessage());
            return null;
        }
    }

    private String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isBlank() || encryptionKey == null || encryptionKey.isBlank()) {
            return null;
        }
        return SmtpPasswordEncryption.decrypt(encrypted, encryptionKey);
    }
}
