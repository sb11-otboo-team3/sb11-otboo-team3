package com.otboo.domain.weather.exception;

import java.time.Instant;
import org.springframework.http.HttpStatus;

// grid+forecastedAt으로 찾은 배치 안에 그 날짜(dayStart 기준) 예보가 하나도 없는, 정상적으로는 있을 수 없는 상태
public class DailyForecastNotFoundException extends WeatherException {

  public DailyForecastNotFoundException(Instant dayStart) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "해당 날짜의 예보를 찾을 수 없습니다: date=" + dayStart);
  }
}