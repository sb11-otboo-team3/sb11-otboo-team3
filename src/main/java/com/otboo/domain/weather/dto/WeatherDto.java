package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.SkyStatus;
import java.time.Instant;
import java.util.UUID;

public record WeatherDto(
    UUID id,
    Instant forecastedAt,
    Instant forecastAt,
    WeatherAPILocation location,
    SkyStatus skyStatus,
    PrecipitationDto precipitation,
    HumidityDto humidity,
    TemperatureDto temperature,
    WindSpeedDto windSpeed
) {

}