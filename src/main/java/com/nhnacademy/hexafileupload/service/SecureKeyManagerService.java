package com.nhnacademy.hexafileupload.service;


import com.nhnacademy.hexafileupload.DTO.KeyResponseDto;
import com.nhnacademy.hexafileupload.exception.KeyManagerException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.List;


// SKM(Secure-Key-Manager)에서 비밀키 가져오는 함수
@Service
@Profile("objectStorage")
public class SecureKeyManagerService {
    // 각각 암호화 된 키
    @Value("${securekey.url}")
    private String url;

    @Value("${securekey.appkey}")
    private String appKey;

    @Value("${keyStoreFilePath}")
    private String keyStoreFilePath;

    @Value("${securekey.password}")
    private String password;

    public String fetchSecretFromKeyManager(String keyId) {
        try {
            // 키 저장소 객체를 만들되 키 유형이 PKCS12인 인스턴스를 가져오기
            KeyStore clientStore = KeyStore.getInstance("PKCS12");

            // 클래스패스에서 keyStoreFilePath로 리소스를 InputStream으로 읽어오기
            try (InputStream keyStoreInputStream = getClass().getClassLoader().getResourceAsStream(keyStoreFilePath)) {
                if (keyStoreInputStream == null) {
                    throw new IllegalStateException("Keystore file not found in classpath: " + keyStoreFilePath);
                }
                // 키스토어 로드
                clientStore.load(keyStoreInputStream, password.toCharArray());
            }

            // SSLContext 설정
            SSLContext sslContext = SSLContexts.custom()
                    .setProtocol("TLS")
                    .loadKeyMaterial(clientStore, password.toCharArray())
                    .loadTrustMaterial(new TrustSelfSignedStrategy())
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .build();

            HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(cm)
                    .evictExpiredConnections()
                    .build();

            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setHttpClient(httpClient);

            RestTemplate restTemplate = new RestTemplate(requestFactory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            URI uri = UriComponentsBuilder
                    .fromUriString(url)
                    .path("/keymanager/v1.0/appkey/{appkey}/secrets/{keyid}")
                    .encode()
                    .build()
                    .expand(appKey, keyId)
                    .toUri();

            ResponseEntity<KeyResponseDto> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), KeyResponseDto.class);
            KeyResponseDto responseBody = response.getBody();
            if (responseBody == null || responseBody.getBody() == null) {
                throw new KeyManagerException("No response body or body content from the keymanager API");
            }

            return responseBody.getBody().getSecret();

        } catch (KeyStoreException | IOException | CertificateException
                 | NoSuchAlgorithmException | UnrecoverableKeyException
                 | KeyManagementException e) {
            throw new KeyManagerException("Error while fetching secret: " + e.getMessage());
        }
    }
}
