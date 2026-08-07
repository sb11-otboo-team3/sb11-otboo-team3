package com.otboo.domain.weather.exception;

import org.springframework.http.HttpStatus;

// gridSaver로 저장까지 했는데도 재조회에서 격자를 못 찾는, 정상적으로는 있을 수 없는 상태
public class GridRegistrationFailedException extends WeatherException {

  public GridRegistrationFailedException(int x, int y) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "격자 등록에 실패했습니다: x=" + x + ", y=" + y);
  }
}