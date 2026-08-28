package com.otboo.domain.weather.dto;

public record HumidityDto(
    double current, //습도
    double comparedToDayBefore //전날이랑 비교
) {

}