package edu.smartcampus;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class GoogleAuthManager {
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    record User(String id, String name, String email, String picture) { }
    record Session(User user, Instant expiresAt) { }

    private final String clientId = System.getenv().getOrDefault("GOOGLE_CLIENT_ID", "").trim();
    private final String clientSecret = System.getenv().getOrDefault("GOOGLE_CLIENT_SECRET", "").trim();
    private final String redirectUri = System.getenv().getOrDefault(
            "GOOGLE_REDIRECT_URI", "http://localhost:8080/auth/google/callback").trim();
    private final String allowedDomain = System.getenv().getOrDefault("GOOGLE_ALLOWED_DOMAIN", "").trim();
    private final String cookieName = "CAMPUSLY_SESSION";
    private final SecureRandom random = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ConcurrentHashMap<String, Instant> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    String redirectUri() {
        return redirectUri;
    }

    String cookieName() {
        return cookieName;
    }

    String beginLogin() {
        if (!isConfigured()) {
            throw new IllegalStateException("Google sign-in is not configured on this server yet.");
        }
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        String state = randomToken();
        states.put(state, now.plus(STATE_TTL));
        return GOOGLE_AUTH_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode("openid email profile")
                + "&state=" + encode(state)
                + "&prompt=select_account";
    }

    User completeLogin(String state, String code) throws Exception {
        Instant expiry = state == null ? null : states.remove(state);
        if (expiry == null || expiry.isBefore(Instant.now()) || code == null || code.isBlank()) {
            throw new IllegalArgumentException("The sign-in request expired or could not be verified. Please try again.");
        }
        String accessToken = exchangeCode(code);
        return loadVerifiedUser(accessToken);
    }

    String createSession(User user) {
        String token = randomToken();
        sessions.put(token, new Session(user, Instant.now().plus(SESSION_TTL)));
        return token;
    }

    User findUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token, session);
            return null;
        }
        return session.user();
    }

    void endSession(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private String exchangeCode(String code) throws Exception {
        String form = "code=" + encode(code)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(redirectUri)
                + "&grant_type=authorization_code";
        HttpRequest request = HttpRequest.newBuilder(URI.create(GOOGLE_TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Google could not verify the sign-in code.");
        }
        Map<String, Object> values = CampusWebServer.parseJsonObject(response.body());
        Object accessToken = values.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("Google did not return a valid sign-in token.");
        }
        return token;
    }

    private User loadVerifiedUser(String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GOOGLE_USERINFO_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Google could not load the signed-in profile.");
        }
        Map<String, Object> values = CampusWebServer.parseJsonObject(response.body());
        String id = string(values.get("sub"));
        String email = string(values.get("email"));
        String verified = string(values.get("email_verified"));
        String domain = string(values.get("hd"));
        if (id.isBlank() || email.isBlank() || !Boolean.parseBoolean(verified)) {
            throw new IllegalArgumentException("Google must provide a verified email address to sign in.");
        }
        if (!allowedDomain.isBlank()
                && (!domain.equalsIgnoreCase(allowedDomain)
                || !email.toLowerCase().endsWith("@" + allowedDomain.toLowerCase()))) {
            throw new IllegalArgumentException("Sign-in is limited to Google accounts from " + allowedDomain + ".");
        }
        String name = string(values.get("name"));
        if (name.isBlank()) {
            name = email;
        }
        return new User(id, name, email, string(values.get("picture")));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
