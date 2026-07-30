package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.WindStrength;

public record WindSpeedDto(
    double speed,
    WindStrength asWord
) {

}
