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
                String wwwAuth = resp.headers().firstValue("WWW-Authenticate").orElse("");
                String responseBody = truncate(resp.body());
                log.warn("Graph sendMail failed: status={} wwwAuth={} body={} from={} to={}",
                        resp.statusCode(), truncate(wwwAuth), responseBody, fromEmail, toEmail);
                // Extract the Graph error code/message for a more actionable exception message
                String graphError = extractGraphError(resp.body());
                throw new IllegalStateException("Graph sendMail HTTP " + resp.statusCode() + ": " + graphError);
            }
        } catch (IllegalStateException e) {
            throw e;
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
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Graph /me failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a human-readable error from a Graph API JSON error response.
     * Example body: {"error":{"code":"ErrorAccessDenied","message":"Access is denied..."}}
     */
    private String extractGraphError(String body) {
        if (body == null || body.isBlank()) return "empty response";
        try {
            var json = MAPPER.readTree(body);
            var error = json.path("error");
            String code = error.path("code").asText(null);
            String message = error.path("message").asText(null);
            if (code != null && message != null) return code + ": " + message;
            if (code != null) return code;
            if (message != null) return message;
        } catch (Exception ignored) {
            // body is not JSON (e.g. HTML auth redirect); return truncated raw body
        }
        return truncate(body);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }
}

