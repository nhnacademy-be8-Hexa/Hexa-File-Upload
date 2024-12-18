package com.nhnacademy.hexafileupload.image.impl;


import com.nhnacademy.hexafileupload.exception.imagemanager.*;
import com.nhnacademy.hexafileupload.image.ImageStore;
import com.nhnacademy.hexafileupload.tool.FileExtensionVaildation;
import com.nhnacademy.hexafileupload.tool.ImageNameSeperator;

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

    @Value("${image.security.key}")
    private String securityKey;

    @Value("${image.base.path}")
    private String basePath;

    private static final String IMAGE_API_URL = "/image/v2.0/appkeys";
    private static final String IMAGE_ENDPOINT = "/images";
    private static final String FOLDER_ENDPOINT = "/folders";

    private static final String AUTH_HEADER = "Authorization";
    private static final String FILES_PARAM = "files";
    private static final String PARAMS_PARAM = "params";


    /*
        다중 파일 업로드

        아래의 예시 요청에 맞게 함수를 구성하였습니다.

        curl -X POST 'https://api-image.nhncloudservice.com/image/v2.0/appkeys/{appKey}/images' \
        -H 'Authorization: {secretKey}' \
        -F 'params={"basepath": "/myfolder/banner", "overwrite": true, "operationIds":["100x100"]}' \
        -F 'files=@left.png' \
        -F 'files=@right.png'


     */

    @Override
    public boolean saveImages(List<MultipartFile> files, String fileName) {

        FileExtensionVaildation fileExtensionVaildation = new FileExtensionVaildation(files);
        fileExtensionVaildation.vaildate(); // 확장자 이상하면 FileExtensitonException 발생

        String fileSaveURL = apiUrl + IMAGE_ENDPOINT;

        RestTemplate restTemplate = new RestTemplate();

        // JSON 파라미터 생성 (nhn cloud 에 저장시 루트기준 path 랑 파일 같으면 덮어쓸지 여부)
        String paramsJson = String.format("{\"basepath\": \"%s\", \"overwrite\": true}", basePath);

        // 멀티 파트 요청 바디 생성 후 json 파라미터 집어넣음
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(PARAMS_PARAM, paramsJson);


        // 파일 이름을 fileName + number 순으로 이름을 변경 + 파일을 이진타입으로 변환
        List<ByteArrayResource> fileCovertResult = FileNameAndBinaryChanger(files,fileName);

        // 변환한 파일 값들을 http body에 넣음
        for(ByteArrayResource byteArrayResource : fileCovertResult){
            body.add(FILES_PARAM, byteArrayResource);
        }

        // 헤더 설정 (Authorization : 개인 키 값)
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, securityKey);

        // HTTP 요청 생성
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        // 이미지 저장 API 호출
        try {
            ResponseEntity<String> response = sendPostRequest(fileSaveURL, entity);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            throw new NHNImageManagerStoreException("NHN Image Manager 에 파일을 저장하지 못했습니다.");
        }
    }


    /*

        다중 파일 삭제

        아래의 예시 요청에 맞게 함수를 구성하였습니다.

        curl -X DELETE 'https://api-image.nhncloudservice.com/image/v2.0/appkeys/{appKey}/images/async?
        fileIds=5fa8ce52-d066-490c-85dd-f8cef181dd28,96f726bd-93e4-4f7c-ad55-56e85aa323a8' \
        -H 'Authorization: {secretKey}'

     */


    @Override
    public boolean deleteImages(String fileName) {

        // 해당 이미지 이름을 가진 이미지 id 들을 모두 가져옴
        List<String> imageIds = getImageIdsToNames(fileName);

        if (imageIds.isEmpty()) {
            return false;  // 이미지가 없으면 삭제 불가
        }

        // 이미지 지우는 요청을 수행할 기본 url 생성
        String deleteUrl = buildDeleteUrl(imageIds);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, securityKey);

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
            throw new NHNImageManagerDeleteException("NHN Image Manager 에 파일을 삭제하지 못했습니다.");
        }
    }



    /*
        폴더 내 파일 목록 조회

        아래의 예시 요청에 맞게 함수를 구성하고 , 결과 값에서 url 부분만 List<String> 형태로 반환하게 작성하였습니다.

        curl -X GET 'https://api-image.nhncloudservice.com/image/v2.0/appkeys/{appKey}/folders?basepath=/myfolder&name=ex1' \
        -H 'Authorization: {secretKey}'

     */


    @Override
    public List<String> getImage(String fileName) {
        // 이미지 url 을 가져올 요청 url 생성
        String getImageUrl = apiUrl + FOLDER_ENDPOINT + "?basepath=" + basePath + "&name=" + fileName;

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, securityKey);

        try {
            ResponseEntity<String> responseEntity = sendGetRequest(getImageUrl, headers);
            return processGetImageResponse(responseEntity.getBody());
        } catch (Exception e) {
            throw new NHNImageManagerSearchException("NHN Image Manager 검색 중 오류가 발생했습니다.");
        }
    }

    // 파일 이름을 fileName + number 순으로 바꾸고 파일을 이진 타입으로 변환해서 반환
    private List<ByteArrayResource> FileNameAndBinaryChanger(List<MultipartFile> files, String fileName){

        List<ByteArrayResource> fileCovertResult= new ArrayList<ByteArrayResource>();

        for (int count = 0; count < files.size(); count++) {
            MultipartFile file = files.get(count);
            ImageNameSeperator imageNameSeperator = new ImageNameSeperator(Objects.requireNonNull(file.getOriginalFilename()));
            imageNameSeperator.setFilename(fileName + "-" + String.format("%03d", count));

            ByteArrayResource resource = createFileResource(file, imageNameSeperator);

            fileCovertResult.add(resource);
        }

        return fileCovertResult;
    }


    // 파일 하나하나를 이진 데이터로 바꾸고 이름을 바꾸는 함수
    private ByteArrayResource createFileResource(MultipartFile file, ImageNameSeperator imageNameSeperator) {
        byte[] fileByte;

        try {
            fileByte = file.getBytes();
        } catch (IOException e) {
            throw new FileCovertBinaryException("파일을 이진 데이터로 변화하는데 에러가 발생했습니다.");
        }

        return new ByteArrayResource(fileByte) {
            @Override
            public String getFilename() {
                return imageNameSeperator.FullName();  // 파일 이름 수정
            }
        };
    }

    //  해당 url 로 post method send 요청을 보내는 함수
    private ResponseEntity<String> sendPostRequest(String url, HttpEntity<?> entity) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            throw new SendPostRequestException("Post request 요청을 보내던 중 에러가 발생했습니다");
        }
    }

    //  해당 url 로 get method send 요청을 보내는 함수
    private ResponseEntity<String> sendGetRequest(String url, HttpHeaders headers) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
        } catch (Exception e) {
            throw new SendGetRequestException("Get request 요청을 보내던 중 에러가 발생했습니다");
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
                throw new FileNotFoundException("파일을 찾을 수 없습니다.");
            }

            return imageUrls;
        } catch (JSONException e) {
            throw new JsonResponseException("응답받은 JSON 형식이 알맞지 않습니다");
        }
    }

    // 이미지 이름을 nhn cloud 에서 검색 한 후 이미지의 id를 가져오는 함수

    /*
    폴더 내 파일 목록 조회

    아래의 예시 요청에 맞게 함수를 구성하고 , 결과 값에서  이미지 id 부분만 List<String> 형태로 가져올 수 있도록 작성하였습니다.

    curl -X GET 'https://api-image.nhncloudservice.com/image/v2.0/appkeys/{appKey}/folders?basepath=/myfolder&name=ex1' \
    -H 'Authorization: {secretKey}'

 */
    private List<String> getImageIdsToNames(String filename) {
        String getImageUrl = apiUrl + FOLDER_ENDPOINT + "?basepath=" + basePath + "&name=" + filename;

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTH_HEADER, securityKey);

        // GET 요청 보내기
        try {
            ResponseEntity<String> responseEntity = sendGetRequest(getImageUrl, headers);
            return extractImageIdsFromResponse(responseEntity.getBody());
        } catch (Exception e) {
            throw new NHNImageManagerSearchException("이미지 ID 조회 중 오류가 발생했습니다.");
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
                throw new ImageNotFoundException("해당하는 이미지를 찾을 수 없습니다");
            }

            return imageIds;
        } catch (JSONException e) {
            throw new JsonResponseException("응답받은 JSON 형식이 알맞지 않습니다");
        }
    }

    // 지울 이미지 주소 반환
    private String buildDeleteUrl(List<String> imageIds) {
        String fileIdsParam = String.join(",", imageIds);
        return String.format("%s?fileIds=%s", apiUrl + "/images/async", fileIdsParam);
    }
}