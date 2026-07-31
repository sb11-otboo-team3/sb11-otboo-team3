package com.otboo.domain.weather.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidWeatherGridException extends WeatherException {

  public InvalidWeatherGridException(double latitude, double longitude) {
    super(HttpStatus.BAD_REQUEST,
        "유효하지 않은 위경도입니다: latitude=" + latitude + ", longitude=" + longitude);
  }
}