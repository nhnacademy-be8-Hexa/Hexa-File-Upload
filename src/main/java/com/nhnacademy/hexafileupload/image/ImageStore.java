package com.nhnacademy.hexafileupload.image;


import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 단일 이미지저장 인터페이스
public interface ImageStore {

    // 여기서 파일의 이름은 확장자명까지 포함한 걸 의미함

    // 파일과 파일의 이름을 받아 파일의 저장 성공 여부를 반환하는 함수
    boolean saveImages(List<MultipartFile> files , String fileName);

    // 파일의 이름을 받아서 파일 이름으로 시작하는 이미지를 지우는 함수
    boolean deleteImages(String fileName);

    // 파일의 이름을 받아서 파일 이름으로 시작하는 url을 가져오는 함수
    List<String> getImage(String fileName);
}
