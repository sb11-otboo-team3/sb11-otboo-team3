package com.otboo.domain.weather.dto;

public record TemperatureDto(
    double current, //현재 온도
    double comparedToDayBefore, //전날과의 비교
    double min, //일일 최저 온도
    double max, //일일 최대 온도
    double average //일일 평균 온도
) {

}