package com.otboo.domain.weather.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public abstract class WeatherException extends OtbooException {

  protected WeatherException() {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "날씨 api에서 오류가 발생했습니다.");
  }

  protected WeatherException(HttpStatus status,String message) {
    super(status, message);
  }
}
