package com.nhnacademy.hexafileupload.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("dev")
public class WebConfig implements WebMvcConfigurer {

    @Value("${upload.dir}")
    private String dir;


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        String uploadDir = System.getProperty("user.home") + dir;


        // 절대경로에 저장된 이미지 파일을 웹에서 접근할 수 있도록 설정
        registry.addResourceHandler(dir+"/**")
                .addResourceLocations("file:"+uploadDir);  // 절대경로 (예: /var/www/uploads/)
    }
}

