package com.otboo.domain.weather.exception;

import org.springframework.http.HttpStatus;

public class KakaoApiException extends WeatherException {

  public KakaoApiException(double latitude, double longitude, Throwable cause) {
    super(HttpStatus.BAD_REQUEST,
        "카카오 API 호출에 실패했습니다: latitude=" + latitude + ", longitude=" + longitude);
    initCause(cause);
  }
}
