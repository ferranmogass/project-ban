package upf.at.ban.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Open Gateway - Verify Age Threshold integration.
 *
 * Supports:
 *  - Mock token mode (default): uses "mock_sandbox_access_token"
 *  - Real backend flow (CIBA): if OPENGW_CLIENT_ID and OPENGW_CLIENT_SECRET are set
 *  - Direct token mode: if OPENGW_ACCESS_TOKEN is set, it uses it as Bearer token
 */
public class OpenGatewayAge {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Sandbox base URL (API gateway)
    private static final String BASE = "https://sandbox.opengateway.telefonica.com/apigateway";

    // Endpoints used by backend authorization flow (CIBA)
    private static final String BC_AUTHORIZE_PATH = "/bc-authorize";
    private static final String TOKEN_PATH = "/token";

    // Verify age threshold endpoint
    private static final String VERIFY_PATH = "/kyc-age-verification/v0.1/verify";

    // Scope (backend guide uses dpv:<purpose>#<scope>)
    private static final String AGE_SCOPE = "dpv:FraudPreventionAndDetection#kyc-age-verification:verify";

    // Convenience mock token (for mock mode)
    private static final String MOCK_ACCESS_TOKEN = "mock_sandbox_access_token";

    // Env vars (optional)
    private static final String ENV_ACCESS_TOKEN = "OPENGW_ACCESS_TOKEN";
    private static final String ENV_CLIENT_ID = "1517c19d-b099-4d3b-86c4-8e5cc7362312";
    private static final String ENV_CLIENT_SECRET = "567fde1d-2012-44fd-8a7e-4256764d1a78";

    public static class AgeCheckResult {
        public boolean ok;               // request succeeded
        public boolean isAboveThreshold; // user meets threshold (>=)
        public String details;           // raw info (errors/response)

        public AgeCheckResult(boolean ok, boolean isAboveThreshold, String details) {
            this.ok = ok;
            this.isAboveThreshold = isAboveThreshold;
            this.details = details;
        }
    }

    // Request body (best-effort field names; tolerant parsing on response)
    private static class VerifyAgeRequest {
        public int ageThreshold;

        public VerifyAgeRequest(int ageThreshold) {
            this.ageThreshold = ageThreshold;
        }
    }

    /**
     * Main entry point used by ClientsResource.
     */
    public static AgeCheckResult verifyAgeThreshold(String phoneE164, int threshold) {
        try {
            String token = getAccessTokenForPhone(phoneE164);
            Client client = ClientBuilder.newClient();

            VerifyAgeRequest payload = new VerifyAgeRequest(threshold);

            String responseJson = client
                    .target(BASE)
                    .path(VERIFY_PATH)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Authorization", "Bearer " + token)
                    .header("x-correlator", java.util.UUID.randomUUID().toString())
                    .post(Entity.entity(payload, MediaType.APPLICATION_JSON_TYPE), String.class);

            boolean allowed = parseAllowedFromVerifyResponse(responseJson);

            return new AgeCheckResult(true, allowed, responseJson);

        } catch (Exception e) {
            System.out.println("AGE API ERROR:");
            e.printStackTrace();

            return new AgeCheckResult(
                false,
                false,
                e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }

    /**
     * Chooses which token strategy to use:
     *  1) If OPENGW_ACCESS_TOKEN exists -> use it directly
     *  2) Else if OPENGW_CLIENT_ID & OPENGW_CLIENT_SECRET exist -> perform CIBA backend flow
     *  3) Else -> use convenience mock token
     */
    private static String getAccessTokenForPhone(String phoneE164) throws Exception {
        String direct = System.getenv(ENV_ACCESS_TOKEN);
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }

        String clientId = System.getenv(ENV_CLIENT_ID);
        String clientSecret = System.getenv(ENV_CLIENT_SECRET);
        if (clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()) {
            return getAccessTokenCiba(phoneE164, clientId.trim(), clientSecret.trim());
        }

        // Default: mock mode
        return MOCK_ACCESS_TOKEN;
    }

    /**
     * Backend authorization flow (CIBA):
     *  Step 1: POST /bc-authorize with Basic auth, form: login_hint + scope
     *  Step 2: POST /token with Basic auth, form: grant_type + auth_req_id
     */
    private static String getAccessTokenCiba(String phoneE164, String clientId, String clientSecret) throws Exception {
        Client client = ClientBuilder.newClient();

        // Step 1: bc-authorize
        Form authorizeForm = new Form();
        authorizeForm.param("login_hint", "tel:" + phoneE164);
        authorizeForm.param("scope", AGE_SCOPE);

        String authResp = client
                .target(BASE)
                .path(BC_AUTHORIZE_PATH)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Authorization", basicAuth(clientId, clientSecret))
                .post(Entity.entity(authorizeForm, MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);

        JsonNode authNode = MAPPER.readTree(authResp);
        String authReqId = textOrNull(authNode, "auth_req_id");
        if (authReqId == null) {
            // Some gateways might return authReqId or similar
            authReqId = textOrNull(authNode, "authReqId");
        }
        if (authReqId == null) {
            throw new IllegalStateException("Missing auth_req_id in bc-authorize response: " + authResp);
        }

        // Step 2: token
        Form tokenForm = new Form();
        tokenForm.param("grant_type", "urn:openid:params:grant-type:ciba");
        tokenForm.param("auth_req_id", authReqId);

        String tokenResp = client
                .target(BASE)
                .path(TOKEN_PATH)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Authorization", basicAuth(clientId, clientSecret))
                .post(Entity.entity(tokenForm, MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);

        JsonNode tokenNode = MAPPER.readTree(tokenResp);
        String accessToken = textOrNull(tokenNode, "access_token");
        if (accessToken == null) {
            throw new IllegalStateException("Missing access_token in token response: " + tokenResp);
        }

        return accessToken;
    }

    private static String basicAuth(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        String b64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + b64;
    }

    /**
     * Response parsing is tolerant because API responses can vary depending on operator/sandbox.
     * We try several likely fields.
     */
    private static boolean parseAllowedFromVerifyResponse(String json) throws Exception {
        JsonNode n = MAPPER.readTree(Objects.requireNonNullElse(json, "{}"));

        // Most likely candidates (best-effort)
        // Examples you might see in different implementations:
        //  - {"isAboveAgeThreshold": true}
        //  - {"isOfAge": true}
        //  - {"verified": true}
        //  - {"ageVerificationResult": {"isOfAge": true}}
        //  - {"data": {"isAboveAgeThreshold": true}}
        Boolean b;

        b = booleanAt(n, "isAboveAgeThreshold");
        if (b != null) return b;

        b = booleanAt(n, "isOfAge");
        if (b != null) return b;

        b = booleanAt(n, "verified");
        if (b != null) return b;

        // Nested common patterns
        b = booleanAt(n.path("data"), "isAboveAgeThreshold");
        if (b != null) return b;

        b = booleanAt(n.path("data"), "isOfAge");
        if (b != null) return b;

        b = booleanAt(n.path("ageVerificationResult"), "isAboveAgeThreshold");
        if (b != null) return b;

        b = booleanAt(n.path("ageVerificationResult"), "isOfAge");
        if (b != null) return b;

        // If not found, be conservative: deny.
        return false;
    }

    private static Boolean booleanAt(JsonNode n, String field) {
        if (n != null && n.has(field) && n.get(field).isBoolean()) {
            return n.get(field).asBoolean();
        }
        return null;
    }

    private static String textOrNull(JsonNode n, String field) {
        if (n != null && n.has(field) && !n.get(field).isNull()) {
            return n.get(field).asText();
        }
        return null;
    }

    /**
     * Normalize phone to something E.164-like.
     * Accepts:
     *  - "+34666..." -> OK
     *  - "34666..."  -> becomes "+34666..."
     *  - "tel:+34666..." -> "+34666..."
     */
    public static String normalizeToE164(String raw) {
        if (raw == null) return null;

        String s = raw.trim();
        if (s.startsWith("tel:")) {
            s = s.substring(4).trim();
        }

        // remove spaces
        s = s.replaceAll("\\s+", "");

        // very light validation
        if (s.startsWith("+")) {
            if (s.length() >= 9 && s.length() <= 16) return s;
            return null;
        }

        // if digits only, prefix "+"
        if (s.matches("\\d{8,15}")) {
            return "+" + s;
        }

        return null;
    }

    /**
     * Minimal JSON string escape for error details.
     */
    public static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}