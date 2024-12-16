package com.nhnacademy.hexafileupload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("objectStorage")
public class AuthService {
    // 인증토큰을 가져오는 과정(시간이 지나면 만료가 되기 때문)
    private final String authUrl;
    private final String tenantId;
    private final String username;
    private final String password;
    private final RestTemplate restTemplate;

    public AuthService(
            @Value("${auth.url}") String authUrl,
            @Value("${auth.tenantId}") String tenantId,
            @Value("${auth.username}") String username,
            @Value("${auth.password}") String password,
            RestTemplate restTemplate,
            SecureKeyManagerService secureKeyManagerService) {
        this.authUrl = secureKeyManagerService.fetchSecretFromKeyManager(authUrl);
        this.tenantId = secureKeyManagerService.fetchSecretFromKeyManager(tenantId);
        this.username = secureKeyManagerService.fetchSecretFromKeyManager(username);
        this.password = secureKeyManagerService.fetchSecretFromKeyManager(password);
        this.restTemplate = restTemplate;
    }

    public String requestToken() {
        try {
            String url = authUrl + "/tokens";
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");

            String body = String.format(
                    "{\"auth\":{\"tenantId\":\"%s\",\"passwordCredentials\":{\"username\":\"%s\",\"password\":\"%s\"}}}",
                    tenantId, username, password);

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            // restTemplate 주입을 사용
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
            return jsonNode.path("access").path("token").path("id").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to authenticate: " + e.getMessage(), e);
        }
    }
}
