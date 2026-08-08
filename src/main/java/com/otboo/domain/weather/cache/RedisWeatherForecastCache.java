package com.otboo.domain.weather.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.util.WeatherGrid;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWeatherForecastCache implements WeatherForecastCache {

  // 캐시 키 접두사
  private static final String KEY_PREFIX = "weather:";
  private static final String DELIMITER = ":";
  private static final Duration TTL = Duration.ofHours(3); //TTL 3시간.

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<WeatherDto> find(WeatherGrid grid, Instant forecastedAt, LocalDate date) {
    String json = redisTemplate.opsForValue().get(key(grid, forecastedAt, date));
    //키로 찾았을때 없으면 빈 응답
    if (json == null) {
      return Optional.empty();
    }
    try {
      // 역직렬화 해서 리턴
      return Optional.of(objectMapper.readValue(json, WeatherDto.class));
    } catch (JsonProcessingException e) {
      log.warn("날씨 캐시 역직렬화 실패 - 캐시 미스로 처리, grid=({},{}), forecastedAt={}, date={}",
          grid.x(), grid.y(), forecastedAt, date, e);
      return Optional.empty();
    }
  }

  @Override
  public void save(WeatherGrid grid, Instant forecastedAt, LocalDate date, WeatherDto forecast) {
    // location은 요청자의 원본 좌표라 격자 단위로 공유되는 이 캐시에 넣으면 안 됨 - 응답 조립 시점에 다시 채워짐.
    WeatherDto withoutLocation = stripLocation(forecast);
    try {
      String json = objectMapper.writeValueAsString(withoutLocation);
      redisTemplate.opsForValue().set(key(grid, forecastedAt, date), json, TTL);
    } catch (JsonProcessingException e) {
      log.error("날씨 캐시 직렬화 실패, grid=({},{}), forecastedAt={}, date={}", grid.x(), grid.y(), forecastedAt, date, e);
      throw new UncheckedIOException("날씨 캐시 직렬화 실패", e);
    }
  }

  // location 제외 dto
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

  // 키 조합
  private String key(WeatherGrid grid, Instant forecastedAt, LocalDate date) { //캐시 키.
    return KEY_PREFIX + grid.x() + DELIMITER + grid.y() + DELIMITER + forecastedAt.getEpochSecond() + DELIMITER + date;
  }
}

