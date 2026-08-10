package com.otboo.domain.weather.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
  private static final TypeReference<List<WeatherDto>> FORECAST_LIST_TYPE = new TypeReference<>() {
  };

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  // 캐시는 어디까지나 보조 저장소 - 읽기/쓰기 무엇이 실패하든 로그만 남기고 흡수한다.
  // 실패를 호출부로 전파하면 이미 확보한 예보 데이터가 있어도 응답 전체가 실패해버린다.
  //
  // 이 발표(forecastedAt)의 날짜별 대표 예보 전체를 키 하나에 통째로 저장한다 - 예전엔 날짜별로
  // 키를 쪼갰었는데, 그러면 일부 날짜 키만 만료/축출되고 나머지는 남는 부분 히트가 가능해져서
  // 불완전한 목록을 완전한 것처럼 돌려주는 문제가 있었다. 하나의 키로 묶으면 Redis GET/SET이
  // 원자적이라 "전부 있거나 전부 없거나"만 가능해진다.
  @Override
  public Optional<List<WeatherDto>> find(WeatherGrid grid, Instant forecastedAt) {
    String json;
    try {
      json = redisTemplate.opsForValue().get(key(grid, forecastedAt));
    } catch (DataAccessException e) {
      log.warn("날씨 캐시 조회 실패 - Redis 접근 불가, 캐시 미스로 처리, grid=({},{}), forecastedAt={}",
          grid.x(), grid.y(), forecastedAt, e);
      return Optional.empty();
    }
    //키로 찾았을때 없으면 빈 응답
    if (json == null) {
      return Optional.empty();
    }
    try {
      // 역직렬화 해서 리턴
      return Optional.of(objectMapper.readValue(json, FORECAST_LIST_TYPE));
    } catch (JsonProcessingException e) {
      log.warn("날씨 캐시 역직렬화 실패 - 캐시 미스로 처리, grid=({},{}), forecastedAt={}",
          grid.x(), grid.y(), forecastedAt, e);
      return Optional.empty();
    }
  }

  @Override
  public void save(WeatherGrid grid, Instant forecastedAt, List<WeatherDto> forecasts) {
    // location은 요청자의 원본 좌표라 격자 단위로 공유되는 이 캐시에 넣으면 안 됨 - 응답 조립 시점에 다시 채워짐.
    List<WeatherDto> withoutLocation = forecasts.stream().map(this::stripLocation).toList();
    String json;
    try {
      json = objectMapper.writeValueAsString(withoutLocation);
    } catch (JsonProcessingException e) {
      log.error("날씨 캐시 직렬화 실패 - 캐시 쓰기 생략, grid=({},{}), forecastedAt={}",
          grid.x(), grid.y(), forecastedAt, e);
      return;
    }
    try {
      redisTemplate.opsForValue().set(key(grid, forecastedAt), json, TTL);
    } catch (DataAccessException e) {
      log.error("날씨 캐시 저장 실패 - Redis 접근 불가, 캐시 쓰기 생략, grid=({},{}), forecastedAt={}",
          grid.x(), grid.y(), forecastedAt, e);
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
  private String key(WeatherGrid grid, Instant forecastedAt) { //캐시 키.
    return KEY_PREFIX + grid.x() + DELIMITER + grid.y() + DELIMITER + forecastedAt.getEpochSecond();
  }
}
