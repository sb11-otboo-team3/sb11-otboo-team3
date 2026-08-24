package com.otboo.global.infrastructure.storage.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;

@SpringBootTest
@ActiveProfiles("test")
class MultipartConfigurationTest {

    @Autowired
    private MultipartProperties multipartProperties;

    @Test
    @DisplayName("Multipart 파일 제한은 이미지 최대 크기인 10 MiB를 허용한다")
    void multipartFileSizeLimitAllowsTenMiBs() {
        assertThat(multipartProperties.getMaxFileSize())
                .isEqualTo(DataSize.ofMegabytes(10));

        assertThat(multipartProperties.getMaxRequestSize())
                .isGreaterThan(DataSize.ofMegabytes(10));
    }
}