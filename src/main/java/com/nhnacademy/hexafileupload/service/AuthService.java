package com.nhnacademy.hexafileupload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.hexafileupload.DTO.TokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;

@Service
@Profile("objectStorage")
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final String authUrl;
    private final String authBody;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AuthService(
            @Value("${auth.url}") String authUrl,
            @Value("${auth.body}") String authBody,
            RestTemplate restTemplate,
            SecureKeyManagerService secureKeyManagerService,
            ObjectMapper objectMapper) {
        this.authUrl = secureKeyManagerService.fetchSecretFromKeyManager(authUrl);
        this.authBody = secureKeyManagerService.fetchSecretFromKeyManager(authBody);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 토큰 ID와 만료 시간을 함께 반환하는 메서드
     *
     * @return TokenInfo 객체
     */
    public TokenInfo requestToken() {
        try {
            String url = authUrl + "/tokens";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(authBody, headers);

            logger.info("Requesting token from auth server: {}", url);

            // POST 요청을 통해 토큰 정보 요청
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            logger.debug("Auth server response status: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.error("Failed to authenticate, status code: {}", response.getStatusCode());
                throw new RuntimeException("Failed to authenticate, status code: " + response.getStatusCode());
            }

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            JsonNode tokenNode = jsonNode.path("access").path("token");

            String tokenId = tokenNode.path("id").asText();
            String expiresStr = tokenNode.path("expires").asText();
            Instant expires = Instant.parse(expiresStr);

            logger.info("Obtained token: {}, expires at: {}", tokenId, expires);

            return new TokenInfo(tokenId, expires);
        } catch (IOException e) {
            logger.error("Failed to parse token response", e);
            throw new RuntimeException("Failed to parse token response: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to authenticate", e);
            throw new RuntimeException("Failed to authenticate: " + e.getMessage(), e);
        }
    }
}
