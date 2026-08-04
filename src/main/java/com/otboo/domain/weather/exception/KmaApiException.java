package com.otboo.domain.weather.exception;

import com.otboo.domain.weather.util.VilageFcstBaseTime;
import org.springframework.http.HttpStatus;

public class KmaApiException extends WeatherException {

  public KmaApiException(int nx, int ny, VilageFcstBaseTime baseTime, Throwable cause) {
    super(HttpStatus.BAD_REQUEST,
        "기상청 API 호출에 실패했습니다: nx=" + nx + ", ny=" + ny + ", baseTime=" + baseTime);
    initCause(cause);
  }
}