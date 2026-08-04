package com.otboo.domain.weather.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.util.WeatherGrid;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWeatherForecastCache implements WeatherForecastCache {

  private static final String KEY_PREFIX = "weather:";
  private static final String DELIMITER = ":";
  private static final Duration TTL = Duration.ofHours(3); //TTL 3시간.

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<List<WeatherDto>> find(WeatherGrid grid, Instant forecastedAt) {
    String json = redisTemplate.opsForValue().get(key(grid, forecastedAt));
    if (json == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, new TypeReference<List<WeatherDto>>() {
      }));
    } catch (JsonProcessingException e) {
      log.warn("날씨 캐시 역직렬화 실패 - 캐시 미스로 처리, grid=({},{}), forecastedAt={}",
          grid.x(), grid.y(), forecastedAt, e);
      return Optional.empty();
    }
  }

  @Override
  public void save(WeatherGrid grid, Instant forecastedAt, List<WeatherDto> forecasts) {
    List<WeatherDto> withoutLocation = forecasts.stream()
        .map(this::stripLocation)
        .toList();
    try {
      String json = objectMapper.writeValueAsString(withoutLocation);
      redisTemplate.opsForValue().set(key(grid, forecastedAt), json, TTL);
    } catch (JsonProcessingException e) {
      log.error("날씨 캐시 직렬화 실패, grid=({},{}), forecastedAt={}", grid.x(), grid.y(), forecastedAt, e);
      throw new UncheckedIOException("날씨 캐시 직렬화 실패", e);
    }
  }

  private WeatherDto stripLocation(WeatherDto dto) {
    return new WeatherDto(
        dto.id(),
        dto.forecastedAt(),
        dto.forecastAt(),
        null,
        dto.skyStatus(),
        dto.precipitation(),
        dto.humidity(),
        dto.temperature(),
        dto.windSpeed()
    );
  }

  private String key(WeatherGrid grid, Instant forecastedAt) { //캐시 키.
    return KEY_PREFIX + grid.x() + DELIMITER + grid.y() + DELIMITER + forecastedAt.getEpochSecond();
  }
}
