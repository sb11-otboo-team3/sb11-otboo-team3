package com.otboo.domain.weather.dto;

public record TemperatureDto(
    double current,
    double comparedToDayBefore,
    double min,
    double max
) {

}