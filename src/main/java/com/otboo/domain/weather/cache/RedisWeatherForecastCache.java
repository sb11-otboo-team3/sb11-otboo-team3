package com.otboo.domain.weather.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisWeatherForecastCache implements WeatherForecastCache {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<List<WeatherDto>> find(WeatherGrid grid, Instant forecastedAt) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public void save(WeatherGrid grid, Instant forecastedAt, List<WeatherDto> forecasts) {
    throw new UnsupportedOperationException("TODO");
  }
}
