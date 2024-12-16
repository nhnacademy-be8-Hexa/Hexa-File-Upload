package com.nhnacademy.hexafileupload.image.impl;


import com.nhnacademy.hexafileupload.tool.ImageNameSeperator;
import com.nhnacademy.hexafileupload.exception.NHNImageManagerException;
import com.nhnacademy.hexafileupload.image.ImageStore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Profile("imageManager")
public class NHNImageManagerStore implements ImageStore {

    // 프로퍼티값 주입
    @Value("${image.api.url}")
    private String apiUrl;

    @Value("${image.app.key}")
    private String appKey;

    @Value("${image.api.key}")
    private String apiKey;

    @Value("${image.store.folder}")
    private String storeFolder;

    private static final String IMAGE_API_URL = "/image/v2.0/appkeys";
    private static final String IMAGE_ENDPOINT = "/images";
    private static final String FOLDER_ENDPOINT = "/folders";

    private static final String AUTH_HEADER = "Authorization";
    private static final String FILES_PARAM = "files";
    private static final String PARAMS_PARAM = "params";

    @Override
    public boolean saveImages(List<MultipartFile> files, String fileName) {
        String fileSaveURL = apiUrl + IMAGE_ENDPOINT;
        RestTemplate restTemplate = new RestTemplate();

        // JSON 파라미터 생성 (nhn cloud 에 저장시 루트기준 path 랑 파일 같으면 덮어쓸지 여부)
        String paramsJson = String.format("{\"basepath\": \"%s\", \"overwrite\": true}", storeFolder);

        // 멀티 파트 요청 바디 생성 후 json 파라미터 집어넣음
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(PARAMS_PARAM, paramsJson);

        // 파일 이름을 fileName + number 순으로 바꿔서 http body 에 넣음
        for (int count = 0; count < files.size(); count++) {
            MultipartFile file = files.get(count);
            ImageNameSeperator imageNameSeperator = new ImageNameSeperator(Objects.requireNonNull(file.getOriginalFilename()));
            imageNameSeperator.setFilename(fileName + "-" + String.format("%03d", count));

            ByteArrayResource resource = createFileResource(file, imageNameSeperator);

            body.add(FILES_PARAM, resource);
        }

        // 헤더 설정 (Authorization : 개인 키 값)
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, apiKey);

        // HTTP 요청 생성
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        // 이미지 저장 API 호출
        try {
            ResponseEntity<String> response = sendPostRequest(fileSaveURL, entity);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            throw new NHNImageManagerException("image store error", e);
        }
    }

    @Override
    public boolean deleteImages(String fileName) {
        List<String> imageIds = getImageIdsToNames(fileName);

        if (imageIds.isEmpty()) {
            return false;  // 이미지가 없으면 삭제 불가
        }

        // 이미지 지우는 요청을 수행할 url 생성
        String deleteUrl = buildDeleteUrl(imageIds);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, apiKey);

        // DELETE 요청 생성
        RequestEntity<Void> requestEntity = RequestEntity
                .delete(deleteUrl)
                .headers(headers)
                .build();

        // 이미지 삭제 API 호출
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            throw new NHNImageManagerException("image delete error", e);
        }
    }

    @Override
    public List<String> getImage(String fileName) {
        // 이미지 url 을 가져올 요청 url 생성
        String getImageUrl = apiUrl + FOLDER_ENDPOINT + "?basepath=" + storeFolder + "&name=" + fileName;

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, apiKey);

        try {
            ResponseEntity<String> responseEntity = sendGetRequest(getImageUrl, headers);
            return processGetImageResponse(responseEntity.getBody());
        } catch (Exception e) {
            throw new NHNImageManagerException("image search error", e);
        }
    }

    private ByteArrayResource createFileResource(MultipartFile file, ImageNameSeperator imageNameSeperator) {
        byte[] fileByte;
        try {
            fileByte = file.getBytes();
        } catch (IOException e) {
            throw new NHNImageManagerException("file read error", e);
        }

        return new ByteArrayResource(fileByte) {
            @Override
            public String getFilename() {
                return imageNameSeperator.FullName();  // 파일 이름 수정
            }
        };
    }

    private ResponseEntity<String> sendPostRequest(String url, HttpEntity<?> entity) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            throw new NHNImageManagerException("POST request error", e);
        }
    }

    private ResponseEntity<String> sendGetRequest(String url, HttpHeaders headers) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
        } catch (Exception e) {
            throw new NHNImageManagerException("GET request error", e);
        }
    }
    // nhn cloud 에서 이미지 이름으로 검색한 이미지의 정보들(json 객체)을 가져와 url 부분만 리스트 형태로 반환
    private List<String> processGetImageResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONObject header = jsonResponse.getJSONObject("header");
            boolean isSuccessful = header.getBoolean("isSuccessful");

            List<String> imageUrls = new ArrayList<>();
            if (isSuccessful) {
                JSONArray files = jsonResponse.getJSONArray("files");
                for (int i = 0; i < files.length(); i++) {
                    JSONObject file = files.getJSONObject(i);
                    imageUrls.add(file.getString("url"));
                }
            } else {
                imageUrls.add("No files found or request failed.");
            }

            return imageUrls;
        } catch (JSONException e) {
            throw new NHNImageManagerException("JSON response error", e);
        }
    }
    // 이미지 이름을 nhn cloud 에서 검색 한 후 이미지의 id를 가져오는 함수
    private List<String> getImageIdsToNames(String filename) {
        String getImageUrl = apiUrl + FOLDER_ENDPOINT + "?basepath=" + storeFolder + "&name=" + filename;

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, apiKey);

        // GET 요청 보내기
        try {
            ResponseEntity<String> responseEntity = sendGetRequest(getImageUrl, headers);
            return extractImageIdsFromResponse(responseEntity.getBody());
        } catch (Exception e) {
            throw new NHNImageManagerException("이미지 ID 조회 중 오류가 발생했습니다.", e);
        }
    }

    // 반환받은 검색받은 응답값 중에 이미지 id만 걸러서 리스트 형태로 반환
    private List<String> extractImageIdsFromResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONObject header = jsonResponse.getJSONObject("header");
            boolean isSuccessful = header.getBoolean("isSuccessful");

            List<String> imageIds = new ArrayList<>();
            if (isSuccessful) {
                JSONArray files = jsonResponse.getJSONArray("files");
                for (int i = 0; i < files.length(); i++) {
                    JSONObject file = files.getJSONObject(i);
                    imageIds.add(file.getString("id"));
                }
            } else {
                throw new NHNImageManagerException("image not found");
            }

            return imageIds;
        } catch (JSONException e) {
            throw new NHNImageManagerException("JSON extract error", e);
        }
    }

    private String buildDeleteUrl(List<String> imageIds) {
        String fileIdsParam = String.join(",", imageIds);
        return String.format("%s?fileIds=%s", apiUrl + "/images/async", fileIdsParam);
    }
}



//import com.nhnacademy.imagecollection.tool.ImageNameSeperator;
//import com.nhnacademy.imagecollection.service.image.ImageInterface;
//import org.json.JSONArray;
//import org.json.JSONObject;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Profile;
//import org.springframework.core.io.ByteArrayResource;
//import org.springframework.http.*;
//import org.springframework.stereotype.Component;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
//@Component
//@Profile("imageManager")
//public class NHNImageManagerStore implements ImageInterface {
//
//    @Value("${image.api.url}")
//    private String apiUrl;
//
//    @Value("${image.app.key}")
//    private String appKey;
//
//    @Value("${image.store.folder}")
//    private String storeFolder;
//
//    @Value("${image.api.key}")
//    private String apiKey;
//
//
//
//    @Override
//    public boolean saveImages(List<MultipartFile> files, String fileName) {
//
//        String fileSaveURL = apiUrl + "/images";
//        RestTemplate restTemplate = new RestTemplate();
//
//        // json 파라미터
//        String paramsJson = String.format("{\"basepath\": \"%s\", \"overwrite\": %s}", storeFolder, "true");
//
//
//        // body에 데이터 담기
//        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//        body.add("params", paramsJson);
//
//        int count =0;
//
//        // 파일 하나씩 이름 바꿔서 body에 넣음
//        for (MultipartFile file : files) {
//
//           ImageNameSeperator imageNameSeperator = new ImageNameSeperator(Objects.requireNonNull(file.getOriginalFilename()));
//           imageNameSeperator.setFilename(fileName+"-"+String.format("%03d",count));
//
//
//            byte[] fileByte = null;
//            try {
//                fileByte = file.getBytes();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//
//            ByteArrayResource resource = new ByteArrayResource(fileByte){
//                @Override
//                public String getFilename() {
//                    return imageNameSeperator.FullName();  // 여기서 파일 이름을 변경
//                }
//            };
//
//            body.add("files", resource);
//            count ++;
//        }
//
//        count = 0;
//
//        // 헤더 생성 및 헤더 추가
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", apiKey);
//
//        // 바디랑 헤더 합쳐서 생성
//        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
//
//        // 요청 보내고 200이면 확인 아니면 error
//        ResponseEntity<String> response = restTemplate.exchange(fileSaveURL, HttpMethod.POST, entity, String.class);
//
//        // 200 이면 이미지 url 리스트로 반환
//        if (response.getStatusCode() == HttpStatus.OK) {
//
//            List<String> imageName = extractNamesFromJson(response.getBody());
//
//
//            return true;
//        }
//
//        else {
//            // 이건 실패
//            return false;
//        }
//
//    }
//
//    @Override
//    public boolean deleteImages(String fileName) {
//
//        String fileDeleteURL = apiUrl + "/images/async";
//
//
//        List<String> ImageID = getImageIdsToNames(fileName);
//
//
//        String fileIdsParam = String.join(",", ImageID);
//
//        String deleteUrl = String.format(
//                "%s?fileIds=%s",
//                fileDeleteURL, fileIdsParam);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", apiKey);
//
//        RequestEntity<Void> requestEntity = RequestEntity
//                .delete(deleteUrl)
//                .headers(headers)
//                .build();
//
//        RestTemplate restTemplate = new RestTemplate();
//        ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
//
//        if (response.getStatusCode().is2xxSuccessful()) {
//            System.out.println("Images deleted successfully.");
//            return true;
//        } else {
//            System.err.println("Failed to delete images: " + response.getStatusCode());
//            return false;
//        }
//
//    }
//
//    @Override
//    public List<String> getImage(String fileName) {
//
//        String getImageUrl = apiUrl + "/folders?basepath=" + storeFolder + "&name=" + fileName;
//
//        // RestTemplate 객체 생성
//        RestTemplate restTemplate = new RestTemplate();
//
//        // 요청 헤더 설정
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", apiKey);
//
//        // GET 요청 보내기
//        ResponseEntity<String> responseEntity = restTemplate.exchange(getImageUrl, HttpMethod.GET,
//                new HttpEntity<>(null, headers), String.class);
//
//        // 응답 본문을 JSON 파싱
//        String responseBody = responseEntity.getBody();
//        JSONObject jsonResponse = new JSONObject(responseBody);
//
//        // JSON에서 파일 정보 추출
//        JSONObject header = jsonResponse.getJSONObject("header");
//        boolean isSuccessful = header.getBoolean("isSuccessful");
//
//        List<String> imageIds = new ArrayList<>();
//
//        if (isSuccessful) {
//            JSONArray files = jsonResponse.getJSONArray("files");
//            if (files.length() > 0) {
//                // 파일 배열에서 모든 id 값을 리스트에 추가
//                for (int i = 0; i < files.length(); i++) {
//                    JSONObject file = files.getJSONObject(i);
//                    imageIds.add(file.getString("url"));
//                }
//            } else {
//                imageIds.add("No files found.");  // 파일이 없을 경우
//            }
//        } else {
//            imageIds.add("Request was not successful.");  // 요청 실패
//        }
//
//        return imageIds;
//
//    }
//
//
//    private List<String> extractNamesFromJson(String jsonString) {
//        List<String> nameList = new ArrayList<>();
//
//        // JSON 문자열을 JSONObject로 파싱
//        JSONObject jsonObject = new JSONObject(jsonString);
//
//        // "successes" 배열을 가져오기
//        JSONArray successes = jsonObject.getJSONArray("successes");
//
//        // successes 배열을 순회하며 name을 추출
//        for (int i = 0; i < successes.length(); i++) {
//            JSONObject success = successes.getJSONObject(i);
//            String name = success.getString("name");
//            nameList.add(name);
//        }
//
//        return nameList;
//    }
//
//    public List<String> getImageIdsToNames(String filename) {
//
//        String getImageUrl = apiUrl + "/folders?basepath=" + storeFolder + "&name=" + filename;
//
//        // RestTemplate 객체 생성
//        RestTemplate restTemplate = new RestTemplate();
//
//        // 요청 헤더 설정
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", apiKey);
//
//        // GET 요청 보내기
//        ResponseEntity<String> responseEntity = restTemplate.exchange(getImageUrl, HttpMethod.GET,
//                new HttpEntity<>(null, headers), String.class);
//
//        // 응답 본문을 JSON 파싱
//        String responseBody = responseEntity.getBody();
//        JSONObject jsonResponse = new JSONObject(responseBody);
//
//        // JSON에서 파일 정보 추출
//        JSONObject header = jsonResponse.getJSONObject("header");
//        boolean isSuccessful = header.getBoolean("isSuccessful");
//
//        List<String> imageIds = new ArrayList<>();
//
//        if (isSuccessful) {
//            JSONArray files = jsonResponse.getJSONArray("files");
//            if (files.length() > 0) {
//                // 파일 배열에서 모든 id 값을 리스트에 추가
//                for (int i = 0; i < files.length(); i++) {
//                    JSONObject file = files.getJSONObject(i);
//                    imageIds.add(file.getString("id"));
//                }
//            } else {
//                imageIds.add("No files found.");  // 파일이 없을 경우
//            }
//        } else {
//            imageIds.add("Request was not successful.");  // 요청 실패
//        }
//
//        return imageIds;
//    }
//
//}





