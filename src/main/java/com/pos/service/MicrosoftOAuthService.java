package com.pos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal OAuth2 Authorization Code flow for Microsoft Identity Platform.
 * Used to obtain refresh tokens for Microsoft Graph delegated sendMail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MicrosoftOAuthService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${ms.oauth.tenant:common}")
    private String tenant;

    @Value("${ms.oauth.client-id:}")
    private String clientId;

    @Value("${ms.oauth.client-secret:}")
    private String clientSecret;

    @Value("${ms.oauth.redirect-uri:}")
    private String redirectUri;

    public String buildAuthorizeUrl(String state) {
        String base = "https://login.microsoftonline.com/" + (tenant == null || tenant.isBlank() ? "common" : tenant) + "/oauth2/v2.0/authorize";
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", clientId);
        q.put("response_type", "code");
        q.put("redirect_uri", redirectUri);
        q.put("response_mode", "query");
        q.put("scope", "offline_access https://graph.microsoft.com/Mail.Send https://graph.microsoft.com/User.Read");
        q.put("state", state);
        return base + "?" + toQuery(q);
    }

    public TokenResponse exchangeCodeForTokens(String code) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalStateException("Microsoft OAuth is not configured (ms.oauth.*)");
        }
        String url = "https://login.microsoftonline.com/" + (tenant == null || tenant.isBlank() ? "common" : tenant) + "/oauth2/v2.0/token";
        log.info("MicrosoftOAuthService.exchangeCodeForTokens: tenant={}, redirectUri={}", tenant, redirectUri);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("scope", "offline_access https://graph.microsoft.com/Mail.Send https://graph.microsoft.com/User.Read");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(toQuery(form)))
                .build();

        try {
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Microsoft token exchange failed: status={} body={}", resp.statusCode(), truncate(resp.body()));
                throw new IllegalStateException("Microsoft token exchange failed");
            }
            JsonNode json = MAPPER.readTree(resp.body());
            String accessToken = json.path("access_token").asText(null);
            String refreshToken = json.path("refresh_token").asText(null);
            return new TokenResponse(accessToken, refreshToken);
        } catch (Exception e) {
            throw new IllegalStateException("Microsoft token exchange failed: " + e.getMessage(), e);
        }
    }

    public TokenResponse refreshAccessToken(String refreshToken) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Microsoft OAuth is not configured (ms.oauth.*)");
        }
        String url = "https://login.microsoftonline.com/" + (tenant == null || tenant.isBlank() ? "common" : tenant) + "/oauth2/v2.0/token";

        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope", "offline_access https://graph.microsoft.com/Mail.Send https://graph.microsoft.com/User.Read");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(toQuery(form)))
                .build();

        try {
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Microsoft token refresh failed: status={} body={}", resp.statusCode(), truncate(resp.body()));
                throw new IllegalStateException("Microsoft token refresh failed");
            }
            JsonNode json = MAPPER.readTree(resp.body());
            String accessToken = json.path("access_token").asText(null);
            String newRefresh = json.path("refresh_token").asText(null);
            return new TokenResponse(accessToken, newRefresh != null && !newRefresh.isBlank() ? newRefresh : refreshToken);
        } catch (Exception e) {
            throw new IllegalStateException("Microsoft token refresh failed: " + e.getMessage(), e);
        }
    }

    private static String toQuery(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(e.getValue() != null ? e.getValue() : "", StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    public record TokenResponse(String accessToken, String refreshToken) {}
}

