package com.otboo.global.infrastructure.storage.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage.image")
public record ImageStorageProperties(

        @Positive(message = "최대 이미지 파일 크기는 양수여야 합니다.")
        long maxFileSizeBytes

) {
}