package com.otboo.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI otbooOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("옷장을 부탁해 API")
                        .description("개인화 의상 추천 서비스 API 문서")
                        .version("v1"));
    }
}
