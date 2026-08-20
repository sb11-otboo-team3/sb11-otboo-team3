package com.otboo.domain.weather.diff;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.weather.diff")
public record WeatherDiffProperties(
    @Positive(message = "발표별 기온 임계값(announcementTempThreshold)은 0보다 커야 합니다.")
    double announcementTempThreshold, // °C, 발표별 기온 급변 임계값

    @Positive(message = "일일별 기온 시간당 임계값(dailyTempRateThresholdPerHour)은 0보다 커야 합니다.")
    double dailyTempRateThresholdPerHour, // °C/h, 일일별 기온 급변 임계값(시간당 변화율)

    @Positive(message = "강수확률 임계값(precipitationProbabilityThreshold)은 0보다 커야 합니다.")
    @DecimalMax(value = "100.0", message = "강수확률 임계값(precipitationProbabilityThreshold)은 100 이하여야 합니다.")
    double precipitationProbabilityThreshold // %p, 강수형태 전환이 없을 때만 보는 보조 규칙
) {
}
