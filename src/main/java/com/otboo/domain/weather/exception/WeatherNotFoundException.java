package com.otboo.domain.weather.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class WeatherNotFoundException extends WeatherException {

  public WeatherNotFoundException(UUID weatherId) {
    super(HttpStatus.BAD_REQUEST, "존재하지 않는 날씨 정보입니다: id=" + weatherId);
  }
}