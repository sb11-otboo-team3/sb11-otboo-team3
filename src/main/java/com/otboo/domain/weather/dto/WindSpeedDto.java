package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.WindStrength;

public record WindSpeedDto(
    double speed, //풍속
    WindStrength asWord //풍속(약함, 보통, 강함)
) {

}
