package com.otboo.domain.weather.client;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import java.time.LocalDateTime;

public record VilageFcstItem(
    LocalDateTime forecastedAt,
    LocalDateTime forecastAt,
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    String precipitationAmountRaw,
    Double precipitationProbability,
    Double humidity,
    Double temperature,
    Double temperatureMin,
    Double temperatureMax,
    Double windSpeed,
    WindStrength windStrength
) {

}