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
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.of(List.of(cached)));

    // when
    WeatherSummaryDto result = weatherSummaryFinder.find(weatherId);

    // then
    assertThat(result.temperature().min()).isEqualTo(18.0);
    assertThat(result.temperature().max()).isEqualTo(27.0);
  }

  @Test
  @DisplayName("캐시에 이 발표의 목록은 있지만 그 날짜가 안에 없으면 DB에서 계산한다")
  void computesTemperatureRangeFromDbWhenCachedListMissesTheDate() {
    // given
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather(weatherId)));

    // 캐시엔 이 발표(forecastedAt)의 목록이 있지만, 다른 날짜(7/31)만 들어있고 정작 필요한 7/30은 없는 상황.
    WeatherDto otherDate = new WeatherDto(
        null, forecastedAt, forecastAt.plusSeconds(86400), null,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 15.0, 22.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.of(List.of(otherDate)));

    Instant dayStart = LocalDate.of(2026, 7, 30).atStartOfDay(KST).toInstant();
    Instant dayEnd = LocalDate.of(2026, 7, 31).atStartOfDay(KST).toInstant();
    List<Weather> dayForecasts = List.of(
        Weather.builder().grid(grid).forecastedAt(forecastedAt).forecastAt(forecastAt)
            .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
            .temperatureMin(18.0).temperatureMax(24.0).build()
    );
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, dayStart, dayEnd)).willReturn(dayForecasts);

    // when
    WeatherSummaryDto result = weatherSummaryFinder.find(weatherId);

    // then
    assertThat(result.temperature().min()).isEqualTo(18.0);
    assertThat(result.temperature().max()).isEqualTo(24.0);
  }

  @Test
  @DisplayName("캐시가 비어 있으면 DB에서 그날 예보들을 모아 최저/최고 기온을 계산한다")
  void computesTemperatureRangeFromDbWhenCacheMiss() {
    // given
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather(weatherId)));
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
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
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, dayStart, dayEnd)).willReturn(dayForecasts);

    // when
    WeatherSummaryDto result = weatherSummaryFinder.find(weatherId);

    // then
    assertThat(result.temperature().min()).isEqualTo(18.0);
    assertThat(result.temperature().max()).isEqualTo(27.0);
  }

  @Test
  @DisplayName("서로 다른 발표(forecastedAt)에 걸쳐 저장된 row까지 모두 합쳐서 그날 최저/최고를 계산한다")
  void combinesRowsAcrossDifferentBatchesForTheSameDate() {
    // given: 오늘처럼 이전 배치가 저장해둔 이른 시간대 row와, 이번 배치가 저장한 늦은 시간대 row가 섞여있는 상황.
    // forecastedAt 조건 없이 날짜 범위로만 조회하니 둘 다 잡혀야 한다.
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather(weatherId)));
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.empty());

    Instant dayStart = LocalDate.of(2026, 7, 30).atStartOfDay(KST).toInstant();
    Instant dayEnd = LocalDate.of(2026, 7, 31).atStartOfDay(KST).toInstant();
    Instant earlierForecastedAt = forecastedAt.minusSeconds(3600 * 6);
    List<Weather> dayForecasts = List.of(
        Weather.builder().grid(grid).forecastedAt(earlierForecastedAt).forecastAt(dayStart.plusSeconds(3600 * 6))
            .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
            .temperatureCurrent(15.0).build(),
        Weather.builder().grid(grid).forecastedAt(forecastedAt).forecastAt(forecastAt)
            .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
            .temperatureMin(18.0).temperatureMax(24.0).build()
    );
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, dayStart, dayEnd)).willReturn(dayForecasts);

    // when
    WeatherSummaryDto result = weatherSummaryFinder.find(weatherId);

    // then: 이른 시간대 row(다른 forecastedAt)의 temperatureCurrent(15.0)까지 min 계산에 포함됨
    assertThat(result.temperature().min()).isEqualTo(15.0);
    assertThat(result.temperature().max()).isEqualTo(24.0);
  }

  @Test
  @DisplayName("temperature 값이 전부 null인 row는 집계에서 제외하고 0.0으로 오염시키지 않는다")
  void excludesRowsWithNoTemperatureDataInsteadOfDefaultingToZero() {
    // given: 온도가 전부 영상인 날인데, 결측된 row가 섞여있어도 0.0이 최저기온으로 잘못 나오면 안 됨.
    UUID weatherId = UUID.randomUUID();
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather(weatherId)));
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.empty());

    Instant dayStart = LocalDate.of(2026, 7, 30).atStartOfDay(KST).toInstant();
    Instant dayEnd = LocalDate.of(2026, 7, 31).atStartOfDay(KST).toInstant();
    List<Weather> dayForecasts = List.of(
        Weather.builder().grid(grid).forecastedAt(forecastedAt).forecastAt(forecastAt)
            .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
            .build(), // temperatureCurrent/Min/Max 전부 null
        Weather.builder().grid(grid).forecastedAt(forecastedAt).forecastAt(forecastAt.plusSeconds(3600))
            .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
            .temperatureMin(18.0).temperatureMax(27.0).build()
    );
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, dayStart, dayEnd)).willReturn(dayForecasts);

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
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.empty());
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        grid, dayStart, dayEnd)).willReturn(List.of());

    // when & then
    assertThatThrownBy(() -> weatherSummaryFinder.find(weatherId))
        .isInstanceOf(DailyForecastNotFoundException.class);
  }
}
