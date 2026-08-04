package com.otboo.domain.weather.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisWeatherForecastCacheTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private RedisWeatherForecastCache cache;

  private final WeatherGrid grid = new WeatherGrid(60, 127);
  private final Instant forecastedAt = Instant.parse("2026-07-31T05:00:00Z");
  private final String key = "weather:60:127:" + forecastedAt.getEpochSecond();

  @BeforeEach
  void setUp() {
    cache = new RedisWeatherForecastCache(redisTemplate, objectMapper);
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
  }

  @Test
  @DisplayName("캐시에 값이 없으면 빈 값을 반환한다")
  void findReturnsEmptyWhenNotCached() {
    // given
    given(valueOperations.get(key)).willReturn(null);

    // when
    Optional<List<WeatherDto>> result = cache.find(grid, forecastedAt);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("저장하면 location을 제거한 뒤 3시간 TTL로 저장한다")
  void saveStripsLocationAndSetsThreeHourTtl() throws Exception {
    // given
    WeatherAPILocation location = new WeatherAPILocation(37.5665, 126.9780, 60, 127,
        new String[]{"서울특별시", "강서구", "마곡동"});
    List<WeatherDto> forecasts = List.of(weatherDto(forecastedAt, location));

    // when
    cache.save(grid, forecastedAt, forecasts);

    // then
    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(eq(key), jsonCaptor.capture(), eq(Duration.ofHours(3)));

    List<WeatherDto> stored = objectMapper.readValue(jsonCaptor.getValue(), new TypeReference<>() {
    });
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).location()).isNull();
    assertThat(stored.get(0).forecastAt()).isEqualTo(forecastedAt);
    assertThat(stored.get(0).temperature().current()).isEqualTo(23.0);
  }

  @Test
  @DisplayName("캐시에 값이 있으면 역직렬화해서 반환한다")
  void findReturnsDeserializedListWhenCached() throws Exception {
    // given
    List<WeatherDto> cached = List.of(weatherDto(forecastedAt, null));
    given(valueOperations.get(key)).willReturn(objectMapper.writeValueAsString(cached));

    // when
    Optional<List<WeatherDto>> result = cache.find(grid, forecastedAt);

    // then
    assertThat(result).isPresent();
    assertThat(result.get()).hasSize(1);
    assertThat(result.get().get(0).forecastAt()).isEqualTo(forecastedAt);
    assertThat(result.get().get(0).location()).isNull();
  }

  @Test
  @DisplayName("캐시에 저장된 값이 손상되어 있으면 캐시 미스로 처리한다")
  void findReturnsEmptyWhenCachedValueIsCorrupted() {
    // given
    given(valueOperations.get(key)).willReturn("not-valid-json{{{");

    // when
    Optional<List<WeatherDto>> result = cache.find(grid, forecastedAt);

    // then
    assertThat(result).isEmpty();
  }

  private WeatherDto weatherDto(Instant forecastAt, WeatherAPILocation location) {
    return new WeatherDto(
        UUID.randomUUID(),
        forecastAt,
        forecastAt,
        location,
        null,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 20.0, 26.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
  }
}
