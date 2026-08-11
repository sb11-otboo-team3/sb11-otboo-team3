package com.otboo.domain.weather.cache;

import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WeatherForecastCache {

  // 이 발표(forecastedAt)의 날짜별 대표 예보 전체를 한 번에 찾는다 - 있으면 전부 있고 없으면 전부 없다(부분 상태 불가).
  Optional<List<WeatherDto>> find(WeatherGrid grid, Instant forecastedAt);

  // 날짜별 대표 예보 전체를 한 덩어리로 저장.
  void save(WeatherGrid grid, Instant forecastedAt, List<WeatherDto> forecasts);
}
