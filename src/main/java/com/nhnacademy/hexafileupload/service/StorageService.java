package com.nhnacademy.hexafileupload.service;


import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("objectStorage")
public class StorageService {

    private final String storageUrl;
    private final String containerName;
    private final AuthService authService;
    private final RestTemplate restTemplate;
    private String cachedTokenId;
    private long tokenExpiryTime;

    // 토큰 유효기간 가정값 (예: 7200 초 = 2시간)
    private static final long TOKEN_VALIDITY_DURATION = 7200 * 1000;

    public StorageService(
            @Value("${storage.url}") String storageUrl,
            @Value("${storage.containername}")  String containerName,
            AuthService authService,
            RestTemplate restTemplate,
            SecureKeyManagerService secureKeyManagerService) {
        this.containerName = secureKeyManagerService.fetchSecretFromKeyManager(containerName);
        this.storageUrl = secureKeyManagerService.fetchSecretFromKeyManager(storageUrl);
        this.authService = authService;
        this.restTemplate = restTemplate;
    }



    public List<String> getImage(String fileName) {
        String url = String.format("%s/%s", storageUrl, containerName);
        List<String> allObjects = getList(url);

        // 파일 이름 필터링 및 전체 URL 생성
        List<String> filteredObjects = allObjects.stream()
                .filter(name -> name.contains(fileName))
                .map(name -> String.format("%s/%s/%s", storageUrl, containerName,name)) // 전체 URL 생성
                .collect(Collectors.toList());

        return filteredObjects;
    }



    private List<String> getList(String url) {
        String tokenId = getTokenId();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Auth-Token", tokenId);

        HttpEntity<String> requestHttpEntity = new HttpEntity<>(null, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestHttpEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody().split("\\r?\\n"));
            } else {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            System.err.println("Error fetching object list: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    public UploadResult uploadFiles(List<MultipartFile> files, String baseFileName) {
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
        int fileCounter = 1; // 파일 번호 카운터 초기화

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                String message = file.getOriginalFilename() + " is empty.";
                failedFiles.add(message);
                continue;
            }

            try (InputStream inputStream = file.getInputStream()) {
                String originalFilename = file.getOriginalFilename();
                String extension = "";

                // 파일 확장자 추출
                int dotIndex = originalFilename.lastIndexOf('.');
                if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
                    extension = originalFilename.substring(dotIndex);
                }

                // 고유한 파일 이름 생성 (기본 파일 이름 + 카운터 + 확장자)
                String uniqueFileName = String.format("%s_%03d%s", baseFileName, fileCounter++, extension);
                boolean success = uploadObject(uniqueFileName, inputStream);
                if (success) {
                    successFiles.add(uniqueFileName);
                } else {
                    failedFiles.add(uniqueFileName + " failed to upload.");
                }
            } catch (IOException e) {
                String message = file.getOriginalFilename() + " failed to upload: " + e.getMessage();
                failedFiles.add(message);

            }
        }

        return new UploadResult(successFiles, failedFiles);
    }



    public static class UploadResult {
        private final List<String> successFiles;
        private final List<String> failedFiles;

        public UploadResult(List<String> successFiles, List<String> failedFiles) {
            this.successFiles = successFiles;
            this.failedFiles = failedFiles;
        }

        public List<String> getSuccessFiles() {
            return successFiles;
        }

        public List<String> getFailedFiles() {
            return failedFiles;
        }
    }
    public boolean uploadObject(String objectName, InputStream inputStream) throws IOException {
        String tokenId = getTokenId();  // 요청 시 토큰 갱신(또는 캐시 토큰 사용)
        String url = String.format("%s/%s/%s", storageUrl, containerName, objectName);

        restTemplate.execute(url, HttpMethod.PUT, request -> {
            request.getHeaders().add("X-Auth-Token", tokenId);
            request.getHeaders().add("Content-Type", "application/octet-stream");
            IOUtils.copy(inputStream, request.getBody());
        }, clientHttpResponse -> null);

        return true;
    }


    public boolean deleteObject(String objectName) {
        String tokenId = getTokenId();
        String url = String.format("%s/%s/%s", storageUrl, containerName, objectName);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Auth-Token", tokenId);
        HttpEntity<String> requestHttpEntity = new HttpEntity<>(null, headers);

        restTemplate.exchange(url, HttpMethod.DELETE, requestHttpEntity, String.class);
        System.out.println("File deleted successfully from: " + url);
        return true;
    }

    /**
     * 토큰 발급/재발급 및 캐싱
     */
    public String getTokenId() {
        if (cachedTokenId == null || System.currentTimeMillis() > tokenExpiryTime) {
            cachedTokenId = authService.requestToken();
            tokenExpiryTime = System.currentTimeMillis() + TOKEN_VALIDITY_DURATION;
        }
        return cachedTokenId;
    }
}
