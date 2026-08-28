package com.otboo.global.infrastructure.search.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(

        @NotBlank(message = "검색 엔드포인트는 필수입니다.")
        String endpoint,

        @NotNull(message = "검색 연결 타임아웃은 필수입니다.")
        @DurationMin(
                millis = 1,
                message = "검색 연결 타임아웃은 1ms 이상이어야 합니다."
        )
        @DurationMax(
                millis = 2_147_483_647L,
                message = "검색 연결 타임아웃은 2147483647ms 이하여야 합니다."
        )
        Duration connectTimeout,

        @NotNull(message = "검색 응답 타임아웃은 필수입니다.")
        @DurationMin(
                millis = 1,
                message = "검색 응답 타임아웃은 1ms 이상이어야 합니다."
        )
        @DurationMax(
                millis = 2_147_483_647L,
                message = "검색 응답 타임아웃은 2147483647ms 이하여야 합니다."
        )
        Duration socketTimeout

) {
}