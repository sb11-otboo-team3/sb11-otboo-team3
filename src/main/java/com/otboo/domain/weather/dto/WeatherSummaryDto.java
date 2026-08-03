package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.SkyStatus;
import java.util.UUID;

// 날씨 요약 정보
public record WeatherSummaryDto(
    UUID weatherId,
    SkyStatus skyStatus,
    PrecipitationDto precipitation,
    TemperatureDto temperature
) {

}
