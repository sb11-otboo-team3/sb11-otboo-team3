package com.otboo.domain.weather.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public abstract class WeatherException extends OtbooException {

  protected WeatherException(HttpStatus status,String message) {
    super(status, message);
  }
}
