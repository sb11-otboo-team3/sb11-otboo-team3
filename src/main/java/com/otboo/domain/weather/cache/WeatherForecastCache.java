package com.otboo.domain.weather.cache;

import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WeatherForecastCache {

  Optional<List<WeatherDto>> find(WeatherGrid grid, Instant forecastedAt);

  void save(WeatherGrid grid, Instant forecastedAt, List<WeatherDto> forecasts);
}
