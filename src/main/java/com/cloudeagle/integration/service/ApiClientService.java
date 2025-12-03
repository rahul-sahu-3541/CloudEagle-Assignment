package com.cloudeagle.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ApiClientService {
    private final RestTemplate rest = new RestTemplate();

    public JsonNode callApi(String url, HttpMethod method, HttpHeaders headers, Object body) {
        HttpEntity<?> entity = (body == null) ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> response = rest.exchange(url, method, entity, JsonNode.class);
        return response.getBody();
    }
}
