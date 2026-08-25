package com.otboo.global.infrastructure.storage.config;

import com.otboo.global.infrastructure.storage.validation.ImageFileValidator;
import java.time.Duration;
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

    private static final Duration S3_API_CALL_TIMEOUT =
            Duration.ofSeconds(30);

    private static final Duration S3_API_CALL_ATTEMPT_TIMEOUT =
            Duration.ofSeconds(10);

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
                .overrideConfiguration(builder -> builder
                        .apiCallTimeout(S3_API_CALL_TIMEOUT)
                        .apiCallAttemptTimeout(
                                S3_API_CALL_ATTEMPT_TIMEOUT
                        )
                )
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties s3Properties) {
        return S3Presigner.builder()
                .region(Region.of(s3Properties.region()))
                .build();
    }
}