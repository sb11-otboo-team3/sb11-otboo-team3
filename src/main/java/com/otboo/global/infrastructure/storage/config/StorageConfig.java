package com.otboo.global.infrastructure.storage.config;

import com.otboo.global.infrastructure.storage.validation.ImageFileValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}