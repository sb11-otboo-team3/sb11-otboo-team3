package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.weather.cache.WeatherForecastCache;
import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.exception.DailyForecastNotFoundException;
import com.otboo.domain.weather.exception.WeatherNotFoundException;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherSummaryFinderTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private WeatherForecastCache weatherForecastCache;

  private WeatherSummaryFinder weatherSummaryFinder;

  private final Grid grid = Grid.builder().x(60).y(127).build();
  private final Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(KST).toInstant();
  private final Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();

  @BeforeEach
  void setUp() {
    weatherSummaryFinder = new WeatherSummaryFinder(weatherRepository, weatherForecastCache);
  }

  private Weather weather(UUID weatherId) {
    return Weather.builder()
        .grid(grid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .temperatureCurrent(23.0)
        .build();
  }

  @Test
  @DisplayName("존재하지 않는 날씨 id로 조회하면 WeatherNotFoundException을 던진다")
  void throwsWeatherNotFoundExceptionWhenWeatherDoesNotExist() {
    // given
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> weatherSummaryFinder.find(weatherId))
        .isInstanceOf(WeatherNotFoundException.class);
  }

  @Test
  @DisplayName("캐시에 그 날짜 예보가 있으면 캐시의 최저/최고 기온을 그대로 쓴다")
  void usesCachedTemperatureRangeWhenCacheHit() {
    // given
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather(weatherId)));

    WeatherDto cached = new WeatherDto(
        null, forecastedAt, forecastAt, null,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 18.0, 27.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt, LocalDate.of(2026, 7, 30)))
        .willReturn(Optional.of(cached));

    // when
    WeatherSummaryDto result = weatherSummaryFinder.find(weatherId);

    // then
    assertThat(result.temperature().min()).isEqualTo(18.0);
    assertThat(result.temperature().max()).isEqualTo(27.0);
  }

  @Test
  @DisplayName("캐시가 비어 있으면 DB에서 그날 예보들을 모아 최저/최고 기온을 계산한다")
  void computesTemperatureRangeFromDbWhenCacheMiss() {
    // given
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather(weatherId)));
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt, LocalDate.of(2026, 7, 30)))
        .willReturn(Optional.empty());

    Instant dayStart = LocalDate.of(2026, 7, 30).atStartOfDay(KST).toInstant();
    Instant dayEnd = LocalDate.of(2026, 7, 31).atStartOfDay(KST).toInstant();
    List<Weather> dayForecasts = List.of(
        Weather.builder().grid(grid).forecastedAt(forecastedAt).forecastAt(forecastAt)
            .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
            .temperatureMin(18.0).temperatureMax(24.0).build(),
        Weather.builder().grid(grid).forecastedAt(forecastedAt)
            .forecastAt(forecastAt.plusSeconds(3600 * 3))
            .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
            .temperatureMin(19.0).temperatureMax(27.0).build()
    );
    given(weatherRepository.findByGridAndForecastedAtAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, forecastedAt, dayStart, dayEnd)).willReturn(dayForecasts);

    // when
    WeatherSummaryDto result = weatherSummaryFinder.find(weatherId);

    // then
    assertThat(result.temperature().min()).isEqualTo(18.0);
    assertThat(result.temperature().max()).isEqualTo(27.0);
  }

  @Test
  @DisplayName("캐시도 비어 있고 DB에도 해당 날짜 예보가 하나도 없으면 DailyForecastNotFoundException을 던진다")
  void throwsDailyForecastNotFoundExceptionWhenNoForecastsForDate() {
    // given
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather(weatherId)));

    Instant dayStart = LocalDate.of(2026, 7, 30).atStartOfDay(KST).toInstant();
    Instant dayEnd = LocalDate.of(2026, 7, 31).atStartOfDay(KST).toInstant();
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt, LocalDate.of(2026, 7, 30)))
        .willReturn(Optional.empty());
    given(weatherRepository.findByGridAndForecastedAtAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, forecastedAt, dayStart, dayEnd)).willReturn(List.of());

    // when & then
    assertThatThrownBy(() -> weatherSummaryFinder.find(weatherId))
        .isInstanceOf(DailyForecastNotFoundException.class);
  }
}
