package com.otboo.global.infrastructure.storage.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(

        @NotBlank(message = "S3 Region은 필수입니다.")
        String region,

        @NotBlank(message = "S3 Bucket은 필수입니다.")
        String bucket,

        @Positive(message = "Presigned URL 만료 시간은 양수여야 합니다.")
        long presignedUrlExpirationSeconds

) {
}