package com.nhnacademy.hexafileupload.image.impl;

import com.nhnacademy.hexafileupload.image.ImageStore;
import com.nhnacademy.hexafileupload.service.StorageService;
import com.nhnacademy.hexafileupload.exception.LocalImageException;
import com.nhnacademy.hexafileupload.tool.FileExtensionVaildation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
@Profile("objectStorage") // 프로파일이 objectStorage 일때만 활성화
public class ObjectStorageStore implements ImageStore {


    private final StorageService storageService;


    public ObjectStorageStore(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public boolean saveImages(List<MultipartFile> files, String fileName) throws LocalImageException {

        FileExtensionVaildation fileExtensionVaildation = new FileExtensionVaildation(files);
        fileExtensionVaildation.vaildate(); // 확장자 이상하면 FileExtensitonException 발생

        StorageService.UploadResult uploadResult = storageService.uploadFiles(files, fileName);
        if (!uploadResult.getFailedFiles().isEmpty()) {
            throw new LocalImageException("Some files failed to upload: " + uploadResult.getFailedFiles());
        }
        return true;
    }

    @Override
    public boolean deleteImages(String fileName) throws LocalImageException {
        boolean success = storageService.deleteObject(fileName);
        if (!success) {
            throw new LocalImageException("Failed to delete image: " + fileName);
        }
        return true;
    }

    @Override
    public List<String> getImage(String fileName) {
        return storageService.getImage(fileName);
    }


}
