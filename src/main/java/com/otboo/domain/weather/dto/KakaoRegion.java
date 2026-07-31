package com.otboo.domain.weather.dto;

public record KakaoRegion(
    String province, //시.도
    String city, // 시, 군, 구
    String district //동, 읍, 면

) {
}