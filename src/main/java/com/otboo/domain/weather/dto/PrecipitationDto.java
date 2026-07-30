package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.PrecipitationType;

public record PrecipitationDto(
    PrecipitationType type, // NONE | RAIN | RAIN_SNOW | SNOW | SHOWER
    double amount, //강수량
    double probability //강수 확률?

) {

}