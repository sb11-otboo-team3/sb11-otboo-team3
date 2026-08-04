package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.SkyStatus;
import java.time.Instant;
import java.util.UUID;

public record WeatherDto(
    UUID id,
    Instant forecastedAt, //예보 발표 시간
    Instant forecastAt, //예보 지정 시간
    WeatherAPILocation location, //위치
    SkyStatus skyStatus, //하늘 상태
    PrecipitationDto precipitation, //강수량
    HumidityDto humidity, //습도
    TemperatureDto temperature, //온도
    WindSpeedDto windSpeed //풍속
) {

}