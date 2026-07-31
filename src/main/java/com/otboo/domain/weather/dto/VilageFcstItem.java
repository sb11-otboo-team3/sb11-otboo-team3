package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import java.time.LocalDateTime;

// 기상청 api에서 필요한 데이터만 담은 dto
public record VilageFcstItem(
    LocalDateTime forecastedAt,
    LocalDateTime forecastAt,
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    Double precipitationAmount,
    Double precipitationProbability,
    Double humidity,
    Double temperature,
    Double temperatureMin,
    Double temperatureMax,
    Double windSpeed,
    WindStrength windStrength
) {

}