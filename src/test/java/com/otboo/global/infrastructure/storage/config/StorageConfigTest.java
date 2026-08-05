package com.otboo.global.infrastructure.storage.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.global.infrastructure.storage.validation.ImageFileValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StorageConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(StorageConfig.class)
                    .withPropertyValues(
                            "app.storage.s3.region=ap-northeast-2",
                            "app.storage.s3.bucket=test-storage-bucket",
                            "app.storage.s3.presigned-url-expiration-seconds=600",
                            "app.storage.image.max-file-size-bytes=10485760"
                    );

    @Test
    @DisplayName("S3와 이미지 저장 설정값을 바인딩하고 필요한 Bean을 등록한다")
    void bindStoragePropertiesAndRegisterBeans() throws Exception {
        // when & then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context).hasSingleBean(S3Properties.class);
            assertThat(context).hasSingleBean(ImageStorageProperties.class);
            assertThat(context).hasSingleBean(ImageFileValidator.class);

            S3Properties s3Properties =
                    context.getBean(S3Properties.class);

            ImageStorageProperties imageStorageProperties =
                    context.getBean(ImageStorageProperties.class);

            assertThat(s3Properties.region())
                    .isEqualTo("ap-northeast-2");

            assertThat(s3Properties.bucket())
                    .isEqualTo("test-storage-bucket");

            assertThat(s3Properties.presignedUrlExpirationSeconds())
                    .isEqualTo(600L);

            assertThat(imageStorageProperties.maxFileSizeBytes())
                    .isEqualTo(10L * 1024 * 1024);
        });
    }

    @Test
    @DisplayName("S3 Bucket 설정이 누락되면 애플리케이션 컨텍스트 생성에 실패한다")
    void failContextWhenS3BucketIsMissing() throws Exception {
        // given
        ApplicationContextRunner invalidContextRunner =
                new ApplicationContextRunner()
                        .withUserConfiguration(StorageConfig.class)
                        .withPropertyValues(
                                "app.storage.s3.region=ap-northeast-2",
                                "app.storage.s3.presigned-url-expiration-seconds=600",
                                "app.storage.image.max-file-size-bytes=10485760"
                        );

        // when & then
        invalidContextRunner.run(context ->
                assertThat(context).hasFailed()
        );
    }

    @Test
    @DisplayName("Presigned URL 만료 시간이 0이면 애플리케이션 컨텍스트 생성에 실패한다")
    void failContextWhenPresignedUrlExpirationIsZero() throws Exception {
        // given
        ApplicationContextRunner invalidContextRunner =
                new ApplicationContextRunner()
                        .withUserConfiguration(StorageConfig.class)
                        .withPropertyValues(
                                "app.storage.s3.region=ap-northeast-2",
                                "app.storage.s3.bucket=test-storage-bucket",
                                "app.storage.s3.presigned-url-expiration-seconds=0",
                                "app.storage.image.max-file-size-bytes=10485760"
                        );

        // when & then
        invalidContextRunner.run(context ->
                assertThat(context).hasFailed()
        );
    }

    @Test
    @DisplayName("최대 이미지 크기가 0이면 애플리케이션 컨텍스트 생성에 실패한다")
    void failContextWhenMaximumImageSizeIsZero() throws Exception {
        // given
        ApplicationContextRunner invalidContextRunner =
                new ApplicationContextRunner()
                        .withUserConfiguration(StorageConfig.class)
                        .withPropertyValues(
                                "app.storage.s3.region=ap-northeast-2",
                                "app.storage.s3.bucket=test-storage-bucket",
                                "app.storage.s3.presigned-url-expiration-seconds=600",
                                "app.storage.image.max-file-size-bytes=0"
                        );

        // when & then
        invalidContextRunner.run(context ->
                assertThat(context).hasFailed()
        );
    }
}