package com.otboo.global.infrastructure.storage.config;

import com.otboo.global.infrastructure.storage.validation.ImageFileValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties({
        S3Properties.class,
        ImageStorageProperties.class
})
public class StorageConfig {

    @Bean
    public ImageFileValidator imageFileValidator(
            ImageStorageProperties imageStorageProperties
    ) {
        return new ImageFileValidator(
                imageStorageProperties.maxFileSizeBytes()
        );
    }

    @Bean
    public S3Client s3Client(S3Properties s3Properties) {
        return S3Client.builder()
                .region(Region.of(s3Properties.region()))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties s3Properties) {
        return S3Presigner.builder()
                .region(Region.of(s3Properties.region()))
                .build();
    }
}
