package com.otboo.domain.weather.dto;

import java.util.List;

public record WeatherAPILocation (

    //위도
    double latitude,

    //경도
    double longitude,

    // 기상청 행정구역 좌표
    int x,
    int y,

    // 행정구역
    List<String> locationNames

){

}
