package com.nhnacademy.hexafileupload.controller;

import com.nhnacademy.hexafileupload.image.ImageStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/upload2")
public class imageController2 {

    @Autowired
    private ImageStore imageInterface;

    @GetMapping
    public String showUploadForm() {
        return "uploadForm";
    }

    @PostMapping
    public String uploadImages(@RequestParam("files") List<MultipartFile> files, Model model) {

        String fileName = "Book-001";
        Boolean filenames = imageInterface.saveImages(files,fileName);

        List<String> imageURLList=imageInterface.getImage(fileName);

        System.out.println(imageURLList);

        //imageInterface.deleteImages(fileName);


        List<String> exName = imageURLList;
        model.addAttribute("exName",exName);


        return "hellotest";
    }

}

