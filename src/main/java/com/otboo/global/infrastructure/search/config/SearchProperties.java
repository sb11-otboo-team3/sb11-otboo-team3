package com.otboo.global.infrastructure.search.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(

        @NotBlank(message = "검색 엔드포인트는 필수입니다.")
        String endpoint

) {
}