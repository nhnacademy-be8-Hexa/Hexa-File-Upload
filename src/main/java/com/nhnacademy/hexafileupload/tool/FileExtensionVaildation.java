package com.nhnacademy.hexafileupload.tool;

import com.nhnacademy.hexafileupload.exception.FileExtensionException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

public class FileExtensionVaildation {

    private final List<MultipartFile> files;

    public FileExtensionVaildation(List<MultipartFile> files) {
        this.files = files;
    }

    public void vaildate(){
        for(MultipartFile file : files){
            String fileName = Objects.requireNonNull(file.getOriginalFilename());
            ImageNameSeperator imageNameSeperator = new ImageNameSeperator(fileName);
            String fileExtension = imageNameSeperator.getFileExtension().toLowerCase();

            List<String> allowedExtensions = List.of("png", "jpeg", "jpg", "gif");

            // 확장자가 유효한지 확인
            if (!allowedExtensions.contains(fileExtension)) {
                // 예외 발생
                throw new FileExtensionException("지원하지 않는 확징자 입니다.");
            }
        }
    }


}
