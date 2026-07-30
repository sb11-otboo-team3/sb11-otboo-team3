package com.otboo.domain.weather.exception;

import org.springframework.http.HttpStatus;

public class KakaoRegionNotFoundException extends WeatherException {

  public KakaoRegionNotFoundException(double latitude, double longitude) {
    super(HttpStatus.BAD_REQUEST,
        "카카오 응답에 행정동 정보가 없습니다: latitude=" + latitude + ", longitude=" + longitude);
  }
}
