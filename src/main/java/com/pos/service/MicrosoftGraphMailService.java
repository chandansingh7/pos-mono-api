package com.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends email via Microsoft Graph (delegated) using /me/sendMail.
 */
@Slf4j
@Service
public class MicrosoftGraphMailService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void sendMail(String accessToken, String fromEmail, String toEmail, String subject, String bodyText) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("subject", subject);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contentType", "Text");
            body.put("content", bodyText);
            message.put("body", body);

            Map<String, Object> toRecipient = new LinkedHashMap<>();
            toRecipient.put("emailAddress", Map.of("address", toEmail));
            message.put("toRecipients", new Object[]{toRecipient});

            // from is inferred by /me/sendMail; we keep fromEmail only for sanity checks/logging.
            payload.put("message", message);
            payload.put("saveToSentItems", true);

            String json = MAPPER.writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.microsoft.com/v1.0/me/sendMail"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Graph sendMail failed: status={} body={}", resp.statusCode(), truncate(resp.body()));
                throw new IllegalStateException("Graph sendMail failed");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Graph sendMail failed: " + e.getMessage(), e);
        }
    }

    public String getMeEmail(String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.microsoft.com/v1.0/me?$select=mail,userPrincipalName"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Graph /me failed: status={} body={}", resp.statusCode(), truncate(resp.body()));
                throw new IllegalStateException("Graph /me failed");
            }
            var json = MAPPER.readTree(resp.body());
            String mail = json.path("mail").asText(null);
            if (mail != null && !mail.isBlank()) return mail;
            return json.path("userPrincipalName").asText(null);
        } catch (Exception e) {
            throw new IllegalStateException("Graph /me failed: " + e.getMessage(), e);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }
}

