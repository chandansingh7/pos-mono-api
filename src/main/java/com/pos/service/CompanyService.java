package com.pos.service;

import com.pos.dto.request.CompanyRequest;
import com.pos.dto.response.CompanyResponse;
import com.pos.entity.Company;
import com.pos.exception.BadRequestException;
import com.pos.exception.ErrorCode;
import com.pos.repository.CompanyRepository;
import com.pos.util.SmtpPasswordEncryption;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ImageStorageService imageStorageService;
    private final CompanyMailSenderFactory companyMailSenderFactory;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MicrosoftOAuthService microsoftOAuthService;
    private final MicrosoftGraphMailService microsoftGraphMailService;

    @Value("${smtp.encryption.key:}")
    private String smtpEncryptionKey;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public CompanyResponse get() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return CompanyResponse.from(company);
    }

    public String getMicrosoftAuthUrl(String state) {
        return microsoftOAuthService.buildAuthorizeUrl(state);
    }

    @Transactional
    public CompanyResponse update(CompanyRequest request, String updatedBy) {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName(request.getName() != null ? request.getName() : "My Store");
            return companyRepository.save(newCompany);
        });
        company.setName(request.getName());
        company.setLogoUrl(request.getLogoUrl());
        company.setFaviconUrl(request.getFaviconUrl());
        company.setAddress(request.getAddress());
        company.setPhone(request.getPhone());
        company.setEmail(request.getEmail());
        if (hasSmtpRequest(request)) {
            applySmtpFromRequest(company, request);
        }
        applyEmailSendMethod(company, request);
        company.setTaxId(request.getTaxId());
        company.setWebsite(request.getWebsite());
        company.setReceiptFooterText(request.getReceiptFooterText());
        company.setReceiptHeaderText(request.getReceiptHeaderText());
        company.setReceiptPaperSize(request.getReceiptPaperSize() != null ? request.getReceiptPaperSize() : "80mm");
        company.setDisplayCurrency(request.getDisplayCurrency() != null && !request.getDisplayCurrency().isBlank() ? request.getDisplayCurrency().trim() : "USD");
        company.setLocale(request.getLocale() != null && !request.getLocale().isBlank() ? request.getLocale().trim() : "en-US");
        company.setCountryCode(request.getCountryCode() != null && !request.getCountryCode().isBlank() ? request.getCountryCode().trim().toUpperCase() : null);
        company.setWeightUnit(request.getWeightUnit() != null && !request.getWeightUnit().isBlank() ? request.getWeightUnit().trim().toLowerCase() : null);
        company.setVolumeUnit(request.getVolumeUnit() != null && !request.getVolumeUnit().isBlank() ? request.getVolumeUnit().trim().toLowerCase() : null);
        company.setPosQuickShiftControls(request.getPosQuickShiftControls() != null ? request.getPosQuickShiftControls() : Boolean.FALSE);
        company.setPosLayout(request.getPosLayout() != null && !request.getPosLayout().isBlank() ? request.getPosLayout().trim() : "grid");
        // Shift behaviour rules (company-level overrides with sensible defaults)
        company.setShiftMaxDifferenceAbsolute(
                request.getShiftMaxDifferenceAbsolute() != null ? request.getShiftMaxDifferenceAbsolute() : BigDecimal.ZERO);
        company.setShiftMinOpenMinutes(
                request.getShiftMinOpenMinutes() != null ? request.getShiftMinOpenMinutes() : 0L);
        company.setShiftMaxOpenHours(
                request.getShiftMaxOpenHours() != null ? request.getShiftMaxOpenHours() : 0L);
        company.setShiftRequireSameDay(
                request.getShiftRequireSameDay() != null ? request.getShiftRequireSameDay() : Boolean.FALSE);
        // Label field visibility (defaults)
        company.setLabelShowName(
                request.getLabelShowName() != null ? request.getLabelShowName() : Boolean.TRUE);
        company.setLabelShowSku(
                request.getLabelShowSku() != null ? request.getLabelShowSku() : Boolean.TRUE);
        company.setLabelShowPrice(
                request.getLabelShowPrice() != null ? request.getLabelShowPrice() : Boolean.TRUE);
        // Label layout (used by Labels screen printing)
        company.setLabelTemplateId(
                request.getLabelTemplateId() != null && !request.getLabelTemplateId().isBlank()
                        ? request.getLabelTemplateId().trim()
                        : "A4_2x4");
        company.setLabelTemplateColumns(
                request.getLabelTemplateColumns() != null ? request.getLabelTemplateColumns() : 2);
        company.setLabelTemplateRows(
                request.getLabelTemplateRows() != null ? request.getLabelTemplateRows() : 4);
        company.setLabelTemplateGapMm(
                request.getLabelTemplateGapMm() != null ? request.getLabelTemplateGapMm() : 6);
        company.setLabelTemplatePagePaddingMm(
                request.getLabelTemplatePagePaddingMm() != null ? request.getLabelTemplatePagePaddingMm() : 8);
        company.setLabelTemplateLabelPaddingMm(
                request.getLabelTemplateLabelPaddingMm() != null ? request.getLabelTemplateLabelPaddingMm() : 4);
        company.setLabelPageWidthMm(
                request.getLabelPageWidthMm() != null ? request.getLabelPageWidthMm() : null);
        company.setLabelPageHeightMm(
                request.getLabelPageHeightMm() != null ? request.getLabelPageHeightMm() : null);
        // Admin-controlled offline behaviour
        company.setOfflineAllowDashboard(
                request.getOfflineAllowDashboard() != null ? request.getOfflineAllowDashboard() : Boolean.TRUE);
        company.setOfflineAllowOrders(
                request.getOfflineAllowOrders() != null ? request.getOfflineAllowOrders() : Boolean.FALSE);
        company.setOfflineAllowPos(
                request.getOfflineAllowPos() != null ? request.getOfflineAllowPos() : Boolean.FALSE);
        company.setUpdatedBy(updatedBy);
        company = companyRepository.save(company);
        log.info("Company settings updated by {}", updatedBy);
        return CompanyResponse.from(company);
    }

    private boolean hasSmtpRequest(CompanyRequest request) {
        return (request.getSmtpProvider() != null && !request.getSmtpProvider().isBlank())
                || (request.getSmtpHost() != null && !request.getSmtpHost().isBlank())
                || (request.getSmtpUsername() != null && !request.getSmtpUsername().isBlank());
    }

    private void applySmtpFromRequest(Company company, CompanyRequest request) {
        String provider = request.getSmtpProvider() != null ? request.getSmtpProvider().trim().toUpperCase() : null;
        if ("GMAIL".equals(provider)) {
            company.setSmtpProvider("GMAIL");
            company.setSmtpHost("smtp.gmail.com");
            company.setSmtpPort(587);
            company.setSmtpStartTls(true);
        } else if ("OUTLOOK".equals(provider)) {
            company.setSmtpProvider("OUTLOOK");
            company.setSmtpHost("smtp.office365.com");
            company.setSmtpPort(587);
            company.setSmtpStartTls(true);
        } else {
            company.setSmtpProvider(request.getSmtpProvider());
            company.setSmtpHost(request.getSmtpHost());
            company.setSmtpPort(request.getSmtpPort() != null ? request.getSmtpPort() : 587);
            company.setSmtpStartTls(request.getSmtpStartTls() != null ? request.getSmtpStartTls() : true);
        }
        company.setSmtpUsername(request.getSmtpUsername());
        if (request.getSmtpPassword() != null && !request.getSmtpPassword().isBlank()) {
            if (smtpEncryptionKey == null || smtpEncryptionKey.isBlank()) {
                log.warn("SMTP_ENCRYPTION_KEY not set; cannot store email password in Settings");
            } else {
                String encrypted = SmtpPasswordEncryption.encrypt(request.getSmtpPassword().trim(), smtpEncryptionKey);
                if (encrypted != null) company.setSmtpPasswordEncrypted(encrypted);
            }
        }
        // Changing SMTP config invalidates verification
        company.setEmailVerifiedAt(null);
    }

    private void applyEmailSendMethod(Company company, CompanyRequest request) {
        String method = request.getEmailSendMethod() != null ? request.getEmailSendMethod().trim().toUpperCase() : null;
        if (method == null || method.isBlank()) return;
        if (!"SMTP".equals(method) && !"MICROSOFT".equals(method)) return;
        company.setEmailSendMethod(method);
    }

    @Transactional
    public CompanyResponse verifyEmail(String updatedBy) {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        if (company == null) {
            throw new BadRequestException(ErrorCode.EM001);
        }
        String to = company.getEmail();
        if (to == null || to.isBlank()) {
            throw new BadRequestException(ErrorCode.EM001);
        }

        // If Microsoft method is selected and connected, verify by sending via Graph.
        if ("MICROSOFT".equalsIgnoreCase(company.getEmailSendMethod()) && company.getMsRefreshTokenEncrypted() != null && !company.getMsRefreshTokenEncrypted().isBlank()) {
            verifyMicrosoftEmail(company, to, updatedBy);
            company = companyRepository.save(company);
            return CompanyResponse.from(company);
        }

        JavaMailSender sender = companyMailSenderFactory.createSender(company);
        if (sender == null) {
            // Allow verification using env-based SMTP (spring.mail.*) so users can start with minimal setup.
            JavaMailSender fallback = mailSenderProvider.getIfAvailable();
            if (fallback == null || mailHost == null || mailHost.isBlank()) {
                throw new BadRequestException(ErrorCode.EM002);
            }
            sender = fallback;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(to);
            helper.setTo(to);
            helper.setSubject("CicdPOS – Email setup verified");
            helper.setText("Your email (receipt) setup is working. You can send order receipts from the Orders page.", false);
            sender.send(message);
            company.setEmailVerifiedAt(LocalDateTime.now());
            company.setUpdatedBy(updatedBy);
            company = companyRepository.save(company);
            log.info("Email verified for company by {}", updatedBy);
            return CompanyResponse.from(company);
        } catch (MailAuthenticationException e) {
            String authMsg = fullCauseMessage(e);
            log.warn("Email verification failed (auth): host={} port={} username={} error={}",
                    company.getSmtpHost(), company.getSmtpPort(), company.getSmtpUsername(), authMsg);
            if (isMicrosoftBasicAuthDisabled(authMsg, company.getSmtpUsername())) {
                throw new BadRequestException(ErrorCode.EM007);
            }
            throw new BadRequestException(ErrorCode.EM006);
        } catch (MailException e) {
            log.warn("Email verification failed (mail): host={} port={} username={} error={}",
                    company.getSmtpHost(), company.getSmtpPort(), company.getSmtpUsername(), e.getMessage());
            throw new BadRequestException(ErrorCode.EM003, e.getMessage());
        } catch (MessagingException e) {
            log.warn("Email verification failed (messaging): host={} port={} username={} error={}",
                    company.getSmtpHost(), company.getSmtpPort(), company.getSmtpUsername(), e.getMessage());
            throw new BadRequestException(ErrorCode.EM003, e.getMessage());
        } catch (Exception e) {
            log.warn("Email verification failed (unexpected): host={} port={} username={} error={}",
                    company.getSmtpHost(), company.getSmtpPort(), company.getSmtpUsername(), e.getMessage());
            throw new BadRequestException(ErrorCode.EM003, e.getMessage());
        }
    }

    private void verifyMicrosoftEmail(Company company, String to, String updatedBy) {
        if (smtpEncryptionKey == null || smtpEncryptionKey.isBlank()) {
            throw new BadRequestException(ErrorCode.EM003);
        }
        String refresh = SmtpPasswordEncryption.decrypt(company.getMsRefreshTokenEncrypted(), smtpEncryptionKey);
        if (refresh == null || refresh.isBlank()) {
            throw new BadRequestException(ErrorCode.EM003);
        }
        try {
            MicrosoftOAuthService.TokenResponse tok = microsoftOAuthService.refreshAccessToken(refresh);
            if (tok.refreshToken() != null && !tok.refreshToken().isBlank()) {
                String enc = SmtpPasswordEncryption.encrypt(tok.refreshToken(), smtpEncryptionKey);
                if (enc != null) company.setMsRefreshTokenEncrypted(enc);
            }
            String me = microsoftGraphMailService.getMeEmail(tok.accessToken());
            company.setMsAccountEmail(me);
            microsoftGraphMailService.sendMail(tok.accessToken(), me, to, "CicdPOS – Email setup verified", "Your Microsoft email setup is working. You can send order receipts from the Orders page.");
            company.setMsConnectedAt(LocalDateTime.now());
            company.setUpdatedBy(updatedBy);
        } catch (IllegalStateException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            log.warn("Microsoft email verification failed: {}", msg);
            // HTTP 401 from Graph = personal Outlook account — not supported for Mail.Send
            if (msg.contains("401")) {
                throw new BadRequestException(ErrorCode.EM005);
            }
            throw new BadRequestException(ErrorCode.EM004, msg);
        } catch (Exception e) {
            log.warn("Microsoft email verification failed (unexpected): {}", e.getMessage());
            throw new BadRequestException(ErrorCode.EM003);
        }
    }

    @Transactional
    public CompanyResponse connectMicrosoft(String code, String updatedBy) {
        log.info("connectMicrosoft: starting code exchange for updatedBy={} (code length={})", updatedBy, code != null ? code.length() : 0);
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company c = new Company();
            c.setName("My Store");
            return companyRepository.save(c);
        });
        if (smtpEncryptionKey == null || smtpEncryptionKey.isBlank()) {
            log.warn("connectMicrosoft: SMTP_ENCRYPTION_KEY is not configured");
            throw new BadRequestException(ErrorCode.EM003);
        }
        try {
            MicrosoftOAuthService.TokenResponse tok = microsoftOAuthService.exchangeCodeForTokens(code);
            if (tok.refreshToken() == null || tok.refreshToken().isBlank()) {
                log.warn("connectMicrosoft: token exchange returned empty refresh token");
                throw new BadRequestException(ErrorCode.EM003);
            }
            String enc = SmtpPasswordEncryption.encrypt(tok.refreshToken(), smtpEncryptionKey);
            if (enc == null) throw new BadRequestException(ErrorCode.EM003);
            company.setMsRefreshTokenEncrypted(enc);
            String me = tok.accessToken() != null && !tok.accessToken().isBlank() ? microsoftGraphMailService.getMeEmail(tok.accessToken()) : null;
            company.setMsAccountEmail(me);
            company.setMsConnectedAt(LocalDateTime.now());
            company.setEmailSendMethod("MICROSOFT");
            company.setUpdatedBy(updatedBy);
            company = companyRepository.save(company);
            log.info("connectMicrosoft: success; msAccountEmail={}, companyId={}", company.getMsAccountEmail(), company.getId());
            return CompanyResponse.from(company);
        } catch (BadRequestException e) {
            log.warn("connectMicrosoft: BadRequestException code={} message={}", ErrorCode.EM003, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Microsoft connect failed: {}", e.getMessage(), e);
            throw new BadRequestException(ErrorCode.EM003);
        }
    }

    @Transactional
    public CompanyResponse disconnectMicrosoft(String updatedBy) {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        if (company == null) {
            throw new BadRequestException(ErrorCode.EM001);
        }
        company.setMsRefreshTokenEncrypted(null);
        company.setMsAccountEmail(null);
        company.setMsConnectedAt(null);
        if ("MICROSOFT".equalsIgnoreCase(company.getEmailSendMethod())) {
            company.setEmailSendMethod("SMTP");
        }
        company.setUpdatedBy(updatedBy);
        company = companyRepository.save(company);
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse uploadLogo(MultipartFile file, String updatedBy) throws IOException {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName("My Store");
            return companyRepository.save(newCompany);
        });
        String url = imageStorageService.storeCompanyFile("logo", file);
        company.setLogoUrl(url);
        company.setUpdatedBy(updatedBy);
        company = companyRepository.save(company);
        log.info("Company logo updated by {}", updatedBy);
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse uploadFavicon(MultipartFile file, String updatedBy) throws IOException {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName("My Store");
            return companyRepository.save(newCompany);
        });
        String url = imageStorageService.storeCompanyFile("favicon", file);
        company.setFaviconUrl(url);
        company.setUpdatedBy(updatedBy);
        company = companyRepository.save(company);
        log.info("Company favicon updated by {}", updatedBy);
        return CompanyResponse.from(company);
    }

    /** Walks the full cause chain and concatenates all messages for reliable error detection. */
    private static String fullCauseMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append(" ");
            t = t.getCause();
        }
        return sb.toString().trim();
    }

    /**
     * Returns true when Microsoft has disabled basic SMTP auth (535 5.7.139),
     * OR the username is a personal Outlook/Hotmail/Live address which can never
     * use basic SMTP auth regardless of password.
     */
    private static boolean isMicrosoftBasicAuthDisabled(String fullMsg, String username) {
        if (fullMsg.contains("5.7.139") || fullMsg.contains("basic authentication is disabled")) {
            return true;
        }
        return username != null && username.toLowerCase().matches(".*@(outlook|hotmail|live)\\..+");
    }
}
