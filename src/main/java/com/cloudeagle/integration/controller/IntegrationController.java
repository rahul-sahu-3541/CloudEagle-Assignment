package com.cloudeagle.integration.controller;

import com.cloudeagle.integration.entity.ExternalAuthToken;
import com.cloudeagle.integration.entity.ExternalSystemConfig;
import com.cloudeagle.integration.entity.UserTemp;
import com.cloudeagle.integration.repository.ExternalAuthTokenRepository;
import com.cloudeagle.integration.repository.ExternalSystemConfigRepository;
import com.cloudeagle.integration.service.GenericIntegrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.cloudeagle.integration.service.GenericIntegrationService.urlencode;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {

    @Autowired
    private ExternalSystemConfigRepository cfgRepo;

    @Autowired
    private GenericIntegrationService integrationService;

    @Autowired
    private ExternalAuthTokenRepository authTokenRepo;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/fetch/{name}")
    public ResponseEntity<List<UserTemp>> fetch(@PathVariable String name) throws Exception {
        ExternalSystemConfig cfg = cfgRepo.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Config not found for: " + name));

        List<UserTemp> users = integrationService.fetchUsers(cfg);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/fetch-get/{name}")
    public ResponseEntity<List<UserTemp>> fetchGet(@PathVariable String name) throws Exception {
        return fetch(name);
    }

    @GetMapping("/oauth/start/{name}")
    public void startOauth(@PathVariable String name, HttpServletResponse resp) throws IOException {
        ExternalSystemConfig cfg = cfgRepo.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Config not found: " + name));
        JsonNode auth = mapper.readTree(cfg.getAuthConfigJson());
        String clientId = auth.get("client_id").asText();
        String redirectUri = auth.get("redirect_uri").asText();
        String authorizeUrl = auth.has("authorize_url")
                ? auth.get("authorize_url").asText()
                : "https://auth.calendly.com/oauth/authorize";

        String state = UUID.randomUUID().toString();

        String url = UriComponentsBuilder.fromHttpUrl(authorizeUrl)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build().toUriString();

        resp.sendRedirect(url);
    }

    @RequestMapping(value = "/oauth/callback", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<String> oauthCallback(@RequestParam(required = false) String code,
                                                @RequestParam(required = false) String state) throws Exception {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("Missing 'code' parameter. This endpoint must be called by the OAuth provider with ?code=... after user consent.");
        }
        ExternalSystemConfig cfg = cfgRepo.findByName("calendly").orElseThrow();
        JsonNode auth = mapper.readTree(cfg.getAuthConfigJson());

        String tokenUrl = auth.get("token_url").asText();
        String clientId = auth.get("client_id").asText();
        String clientSecret = auth.get("client_secret").asText();
        String redirectUri = auth.get("redirect_uri").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=authorization_code&client_id=" + urlencode(clientId)
                + "&client_secret=" + urlencode(clientSecret)
                + "&redirect_uri=" + urlencode(redirectUri)
                + "&code=" + urlencode(code);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> tokenResp =
                restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, JsonNode.class);

        if (!tokenResp.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(tokenResp.getStatusCode()).body("Token exchange failed");
        }

        JsonNode tokenBody = tokenResp.getBody();

        ExternalAuthToken token = new ExternalAuthToken();
        token.setExternalSystem(cfg.getName());
        token.setAccessToken(tokenBody.get("access_token").asText());
        token.setRefreshToken(tokenBody.has("refresh_token") ? tokenBody.get("refresh_token").asText() : null);
        token.setExpiresAt(
                Instant.now().plusSeconds(
                        tokenBody.has("expires_in") ? tokenBody.get("expires_in").asLong() : 7200
                )
        );

        authTokenRepo.save(token);

        return ResponseEntity.ok("Authorized — token saved.");
    }
}