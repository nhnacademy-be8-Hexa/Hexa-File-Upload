package com.nhnacademy.hexafileupload.image.impl;

import com.nhnacademy.hexafileupload.tool.ImageNameSeperator;
import com.nhnacademy.hexafileupload.exception.LocalImageException;
import com.nhnacademy.hexafileupload.image.ImageStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Component
@Profile("dev") // 프로파일이 local 일때만 활성화
public class LocalImageStore implements ImageStore {

    // 홈디렉토리 아래 이미지 저장 디렉토리 설정
    @Value("${upload.dir}")
    private String dir;

    @Override
    public boolean saveImages(List<MultipartFile> files, String specifyFileName) {
        List<String> ImagesFileName = new ArrayList<String>();
        int image_count = 0;

        for (MultipartFile file : files) {
            // 파일 이름 지정 (예: 고유한 이름이나 원본 파일 이름 등)
            ImageNameSeperator imageNameSeperator = new ImageNameSeperator(Objects.requireNonNull(file.getOriginalFilename()));
            imageNameSeperator.setFilename(specifyFileName + "-" + String.format("%03d", image_count));

            Path folderPath = Paths.get(System.getProperty("user.home") + dir);


            if (!Files.exists(folderPath)) {
                try {
                    Files.createDirectory(folderPath);
                } catch (Exception e) {
                }
            }


            Path filePath = Paths.get(System.getProperty("user.home") + dir, imageNameSeperator.FullName());
            if (!Files.exists(filePath)) {
                try {
                    Files.copy(file.getInputStream(), filePath);
                } catch (IOException e) {
                    throw new LocalImageException("file create error");
                }
            }

            image_count++;
            ImagesFileName.add(imageNameSeperator.FullName());

        }

        return true;
    }

    @Override
    public boolean deleteImages(String fileName) {
        try {
            Files.walkFileTree(Paths.get(System.getProperty("user.home") + dir), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().startsWith(fileName)) {
                        Files.delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (IOException e) {
            throw new LocalImageException("image delete error", e);
        }

        return true;
    }

    @Override
    public List<String> getImage(String fileName) {
        List<String> imageURLList = new ArrayList<String>();

        try {
            Files.walkFileTree(Paths.get(System.getProperty("user.home") + dir), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().startsWith(fileName)) {
                        imageURLList.add(dir + "/" + file.getFileName().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (IOException e) {
            throw new LocalImageException("image search error", e);
        }

        return imageURLList;
    }
}
