package com.otboo.domain.weather.util;

//기상청 격자 단위인 x,y
public record WeatherGrid(
    int x,
    int y
) {
}