package com.cloudeagle.integration.service;

import com.cloudeagle.integration.entity.ExternalSystemConfig;
import com.cloudeagle.integration.entity.UserTemp;
import com.cloudeagle.integration.repository.UserTempRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GenericIntegrationService {

    private final ApiClientService apiClient;
    private final UserTempRepository userTempRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public GenericIntegrationService(ApiClientService apiClient, UserTempRepository userTempRepo) {
        this.apiClient = apiClient;
        this.userTempRepo = userTempRepo;
    }

    public List<UserTemp> fetchUsers(ExternalSystemConfig cfg) throws Exception {
        String baseUrl = normalizeUrl(cfg.getBaseUrl());
        String endpoint = normalizeEndpoint(cfg.getUserEndpoint());

        // Build initial headers based on auth type (may perform token fetch for oauth)
        HttpHeaders baseHeaders = buildAuthHeaders(cfg);
        baseHeaders.set("Accept", "application/json");

        // Determine pagination strategy
        JsonNode paginationCfg = null;
        if (cfg.getPaginationConfigJson() != null && !cfg.getPaginationConfigJson().isBlank()) {
            try { paginationCfg = mapper.readTree(cfg.getPaginationConfigJson()); } catch (Exception ex) { paginationCfg = null; }
        }
        String paginationType = paginationCfg != null && paginationCfg.has("type") ? paginationCfg.get("type").asText() : "none";

        List<UserTemp> accumulated = new ArrayList<>();

        switch (paginationType.toLowerCase()) {
            case "page":
                int page = paginationCfg.has("startPage") ? paginationCfg.get("startPage").asInt(1) : 1;
                int pageSize = paginationCfg.has("pageSize") ? paginationCfg.get("pageSize").asInt(100) : (paginationCfg.has("pageSizeParam") ? paginationCfg.get("pageSizeParam").asInt(100) : 100);
                String pageParam = paginationCfg.has("pageParam") ? paginationCfg.get("pageParam").asText("page") : "page";
                String pageSizeParam = paginationCfg.has("pageSizeParam") ? paginationCfg.get("pageSizeParam").asText("page_size") : "page_size";

                while (true) {
                    String url = baseUrl + endpoint + (endpoint.contains("?") ? "&" : "?") + pageParam + "=" + page + "&" + pageSizeParam + "=" + pageSize;
                    JsonNode resp = apiClient.callApi(url, HttpMethod.GET, baseHeaders, null);
                    List<UserTemp> pageUsers = extractUsersFromResponse(resp, cfg.getName(), paginationCfg);
                    if (pageUsers.isEmpty()) break;
                    accumulated.addAll(pageUsers);
                    if (pageUsers.size() < pageSize) break;
                    page++;
                }
                break;

            case "offset":
                int offset = paginationCfg.has("start") ? paginationCfg.get("start").asInt(0) : 0;
                int limit = paginationCfg.has("limit") ? paginationCfg.get("limit").asInt(100) : (paginationCfg.has("limitParam") ? paginationCfg.get("limitParam").asInt(100) : 100);
                String offsetParam = paginationCfg.has("offsetParam") ? paginationCfg.get("offsetParam").asText("offset") : "offset";
                String limitParam = paginationCfg.has("limitParam") ? paginationCfg.get("limitParam").asText("limit") : "limit";

                while (true) {
                    String url = baseUrl + endpoint + (endpoint.contains("?") ? "&" : "?") + offsetParam + "=" + offset + "&" + limitParam + "=" + limit;
                    JsonNode resp = apiClient.callApi(url, HttpMethod.GET, baseHeaders, null);
                    List<UserTemp> chunk = extractUsersFromResponse(resp, cfg.getName(), paginationCfg);
                    if (chunk.isEmpty()) break;
                    accumulated.addAll(chunk);
                    if (chunk.size() < limit) break;
                    offset += limit;
                }
                break;

            case "cursor":
                String cursorParam = paginationCfg != null && paginationCfg.has("cursorParam") ? paginationCfg.get("cursorParam").asText("cursor") : "cursor";
                String nextField = paginationCfg != null && paginationCfg.has("nextField") ? paginationCfg.get("nextField").asText("next") : "next";
                String dataPath = paginationCfg != null && paginationCfg.has("dataPath") ? paginationCfg.get("dataPath").asText("collection") : "collection";

                String cursor = null;
                while (true) {
                    String url = baseUrl + endpoint;
                    if (cursor != null) url = url + (url.contains("?") ? "&" : "?") + cursorParam + "=" + cursor;
                    JsonNode resp = apiClient.callApi(url, HttpMethod.GET, baseHeaders, null);
                    List<UserTemp> pageUsers = extractUsersFromResponse(resp, cfg.getName(), paginationCfg);
                    accumulated.addAll(pageUsers);

                    // find next cursor value in response
                    if (resp != null) {
                        if (resp.has(nextField) && !resp.get(nextField).isNull()) {
                            cursor = resp.get(nextField).asText();
                            if (cursor == null || cursor.isBlank()) break;
                        } else {
                            // try nested path: resp.data.next or resp.pagination.next
                            if (resp.has("pagination") && resp.get("pagination").has(nextField)) {
                                cursor = resp.get("pagination").get(nextField).asText();
                                if (cursor == null || cursor.isBlank()) break;
                            } else {
                                break;
                            }
                        }
                    } else break;
                }
                break;

            case "none":
            default:
                String url = baseUrl + endpoint;

                // Handle API_KEY in query if specified
                if (cfg.getAuthType() != null && cfg.getAuthType().equalsIgnoreCase("API_KEY")) {
                    try {
                        JsonNode auth = mapper.readTree(cfg.getAuthConfigJson() == null ? "{}" : cfg.getAuthConfigJson());
                        if (auth.has("in") && auth.get("in").asText().equalsIgnoreCase("query") && auth.has("name") && auth.has("value")) {
                            String pname = auth.get("name").asText();
                            String pval = auth.get("value").asText();
                            url = url + (url.contains("?") ? "&" : "?") + pname + "=" + urlencode(pval);
                        }
                    } catch (Exception ex) {
                        // ignore and proceed
                    }
                }

                JsonNode resp = apiClient.callApi(url, HttpMethod.GET, baseHeaders, null);
                accumulated.addAll(extractUsersFromResponse(resp, cfg.getName(), paginationCfg));
                break;
        }

        if (!accumulated.isEmpty()) {
            userTempRepo.saveAll(accumulated);
        }

        return accumulated;
    }

    // Build headers based on auth config. Supports TOKEN, API_KEY (header), BASIC, OAUTH2_CLIENT_CREDENTIALS
    private HttpHeaders buildAuthHeaders(ExternalSystemConfig cfg) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String authType = cfg.getAuthType();
        if (authType == null) return headers;

        JsonNode auth = null;
        try { auth = mapper.readTree(cfg.getAuthConfigJson() == null ? "{}" : cfg.getAuthConfigJson()); } catch (Exception ex) { auth = null; }

        switch (authType.toUpperCase()) {
            case "TOKEN":
                if (auth != null && auth.has("token")) {
                    headers.set("Authorization", "Bearer " + auth.get("token").asText());
                }
                break;

            case "API_KEY":
                if (auth != null && auth.has("in") && auth.get("in").asText().equalsIgnoreCase("header") && auth.has("name") && auth.has("value")) {
                    headers.set(auth.get("name").asText(), auth.get("value").asText());
                }
                break;

            case "BASIC":
                if (auth != null && auth.has("username") && auth.has("password")) {
                    String creds = auth.get("username").asText() + ":" + auth.get("password").asText();
                    String encoded = java.util.Base64.getEncoder().encodeToString(creds.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    headers.set("Authorization", "Basic " + encoded);
                }
                break;

            case "OAUTH2_CLIENT_CREDENTIALS":
                if (auth != null && auth.has("token_url") && auth.has("client_id") && auth.has("client_secret")) {
                    String tokenUrl = auth.get("token_url").asText();
                    String clientId = auth.get("client_id").asText();
                    String clientSecret = auth.get("client_secret").asText();
                    String scope = auth.has("scope") ? auth.get("scope").asText() : null;

                    String cacheKey = tokenUrl + "|" + clientId + "|" + (scope == null ? "" : scope);
                    String accessToken = getCachedOrFetchToken(cacheKey, tokenUrl, clientId, clientSecret, scope);
                    if (accessToken != null) {
                        headers.set("Authorization", "Bearer " + accessToken);
                    }
                }
                break;

            case "NONE":
            default:
                // no auth
                break;
        }

        return headers;
    }

    private String getCachedOrFetchToken(String cacheKey, String tokenUrl, String clientId, String clientSecret, String scope) throws Exception {
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.expiry.isAfter(Instant.now().plusSeconds(30))) {
            return cached.token;
        }

        // fetch token via client_credentials
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        StringBuilder body = new StringBuilder();
        body.append("grant_type=client_credentials");
        body.append("&client_id=").append(urlencode(clientId));
        body.append("&client_secret=").append(urlencode(clientSecret));
        if (scope != null && !scope.isBlank()) body.append("&scope=").append(urlencode(scope));

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<JsonNode> resp = restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, JsonNode.class);
        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().has("access_token")) {
            String token = resp.getBody().get("access_token").asText();
            long expiresIn = resp.getBody().has("expires_in") ? resp.getBody().get("expires_in").asLong(3600) : 3600;
            CachedToken ct = new CachedToken(token, Instant.now().plusSeconds(expiresIn));
            tokenCache.put(cacheKey, ct);
            return token;
        }
        return null;
    }

    private static class CachedToken {
        String token;
        Instant expiry;
        CachedToken(String token, Instant expiry) { this.token = token; this.expiry = expiry; }
    }

    // Extract users from a response. Uses same heuristics as before but supports optional dataPath in pagination config
    private List<UserTemp> extractUsersFromResponse(JsonNode response, String systemName, JsonNode paginationCfg) {
        List<UserTemp> result = new ArrayList<>();
        if (response == null) return result;

        // If paginationCfg specifies dataPath, try to use it (dot separated)
        if (paginationCfg != null && paginationCfg.has("dataPath")) {
            String dataPath = paginationCfg.get("dataPath").asText();
            JsonNode node = getByPath(response, dataPath);
            if (node != null && node.isArray()) {
                for (JsonNode n : node) result.add(parseUserNode(n, systemName));
                return result;
            }
        }

        if (response.has("collection") && response.get("collection").isArray()) {
            for (JsonNode node : response.get("collection")) result.add(parseUserNode(node, systemName));
            return result;
        }
        if (response.isArray()) {
            for (JsonNode node : response) result.add(parseUserNode(node, systemName));
            return result;
        }
        if (response.has("data") && response.get("data").isArray()) {
            for (JsonNode node : response.get("data")) result.add(parseUserNode(node, systemName));
            return result;
        }

        // fallback: find first array field
        Iterator<String> it = response.fieldNames();
        while (it.hasNext()) {
            String f = it.next();
            JsonNode cand = response.get(f);
            if (cand != null && cand.isArray()) {
                for (JsonNode node : cand) result.add(parseUserNode(node, systemName));
                break;
            }
        }

        return result;
    }

    // Helper to navigate dotted path like "data.items"
    private JsonNode getByPath(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) return null;
        String[] parts = path.split("\\.");
        JsonNode cur = root;
        for (String p : parts) {
            if (cur.has(p)) cur = cur.get(p);
            else return null;
        }
        return cur;
    }

    // existing parseUserNode from earlier implementation
    private UserTemp parseUserNode(JsonNode node, String systemName) {
        UserTemp u = new UserTemp();
        u.setExternalSystem(systemName);

        if (node.has("email")) u.setEmail(safeText(node.get("email")));
        else if (node.has("contact") && node.get("contact").has("email")) u.setEmail(safeText(node.get("contact").get("email")));
        else if (node.has("attributes") && node.get("attributes").has("email")) u.setEmail(safeText(node.get("attributes").get("email")));

        if (node.has("name")) u.setName(safeText(node.get("name")));
        else if (node.has("full_name")) u.setName(safeText(node.get("full_name")));
        else if (node.has("first_name") || node.has("last_name")) {
            StringBuilder sb = new StringBuilder();
            if (node.has("first_name")) sb.append(safeText(node.get("first_name")));
            if (node.has("last_name")) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(safeText(node.get("last_name")));
            }
            u.setName(sb.toString());
        }

        try { u.setRawJson(mapper.writeValueAsString(node)); } catch (Exception ex) { u.setRawJson(node.toString()); }
        return u;
    }

    private String safeText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        return node.toString();
    }

    private String normalizeUrl(String baseUrl) {
        if (baseUrl == null) return "";
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return "";
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    public static String urlencode(String s) {
        try { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ex) { return s; }
    }
}