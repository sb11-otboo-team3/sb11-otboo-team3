package com.otboo.domain.weather.cache;

import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface WeatherForecastCache {

  // 날씨 찾기
  Optional<WeatherDto> find(WeatherGrid grid, Instant forecastedAt, LocalDate date);

  //날씨 저장.
  void save(WeatherGrid grid, Instant forecastedAt, LocalDate date, WeatherDto forecast);
}
