package com.nhnacademy.hexafileupload.controller;

import com.nhnacademy.hexafileupload.image.ImageStore;
import com.nhnacademy.hexafileupload.exception.LocalImageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class ImageController {

    private final ImageStore imageStore;

    @Autowired
    public ImageController(ImageStore imageStore) {
        this.imageStore = imageStore;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/images/upload")
    public String showUploadForm() {
        return "upload";
    }

    @PostMapping("/images/upload")
    public String uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("specifyFileName") String specifyFileName,
            Model model) {
        try {
            boolean success = imageStore.saveImages(files, specifyFileName);
            if (success) {
                model.addAttribute("message", "이미지 업로드가 성공적으로 완료되었습니다.");
                return "redirect:/images/list";
            } else {
                model.addAttribute("message", "이미지 업로드에 실패했습니다.");
                return "upload";
            }
        } catch (LocalImageException e) {
            model.addAttribute("message", "이미지 업로드 중 오류가 발생했습니다: " + e.getMessage());
            return "upload";
        }
    }

    @GetMapping("/images/list")
    public String listImages(Model model) {
        // 모든 이미지를 가져오기 위해 빈 문자열을 전달
        List<String> allImages = imageStore.getImage("");
        model.addAttribute("images", allImages);
        return "list";
    }


    @PostMapping("/images/delete")
    public String deleteImage(@RequestParam("fileName") String fileName, Model model) {
        try {
            boolean success = imageStore.deleteImages(fileName);
            if (success) {
                model.addAttribute("message", "이미지 삭제가 성공적으로 완료되었습니다.");
            } else {
                model.addAttribute("message", "이미지 삭제에 실패했습니다.");
            }
        } catch (LocalImageException e) {
            model.addAttribute("message", "이미지 삭제 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/images/list";
    }
}
