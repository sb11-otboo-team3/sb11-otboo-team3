package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherPersisterTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private WeatherSaver weatherSaver;

  private WeatherPersister weatherPersister;

  private final Grid grid = Grid.builder().x(60).y(127).build();
  private final WeatherAPILocation location = new WeatherAPILocation(37.5665, 126.9780, 60, 127,
      List.of("서울특별시", "강서구", "마곡동"));

  @BeforeEach
  void setUp() {
    weatherPersister = new WeatherPersister(weatherRepository, weatherSaver);
  }

  private VilageFcstItem item(LocalDateTime forecastAt, SkyStatus skyStatus, PrecipitationType precipitationType) {
    return new VilageFcstItem(
        LocalDateTime.of(2026, 7, 30, 5, 0),
        forecastAt,
        skyStatus,
        precipitationType,
        0.0,
        20.0,
        55.0,
        23.0,
        null,
        null,
        2.3
    );
  }

  @Test
  @DisplayName("전날 같은 시각 기록이 없으면 전일 대비 값 없이 저장한다")
  void savesWithoutComparedToDayBeforeWhenNoYesterdayRecord() {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    given(weatherRepository.findByGridAndForecastAt(grid, dayBeforeForecastAt))
        .willReturn(Optional.empty());
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().humidity().comparedToDayBefore()).isEqualTo(0.0);
    assertThat(result.get().temperature().comparedToDayBefore()).isEqualTo(0.0);

    ArgumentCaptor<Weather> captor = ArgumentCaptor.forClass(Weather.class);
    verify(weatherSaver).upsertInNewTransaction(captor.capture());
    assertThat(captor.getValue().getHumidityComparedToDayBefore()).isNull();
    assertThat(captor.getValue().getTemperatureComparedToDayBefore()).isNull();
  }

  @Test
  @DisplayName("하루 전 같은 시각 기록이 있으면 전일 대비 값을 계산해서 저장한다")
  void computesComparedToDayBeforeWhenYesterdayRecordExists() {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    Weather yesterday = Weather.builder()
        .grid(grid)
        .forecastedAt(dayBeforeForecastAt)
        .forecastAt(dayBeforeForecastAt)
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.NONE)
        .humidityCurrent(50.0)
        .temperatureCurrent(20.0)
        .build();
    given(weatherRepository.findByGridAndForecastAt(grid, dayBeforeForecastAt))
        .willReturn(Optional.of(yesterday));
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    ArgumentCaptor<Weather> captor = ArgumentCaptor.forClass(Weather.class);
    verify(weatherSaver).upsertInNewTransaction(captor.capture());
    assertThat(captor.getValue().getHumidityComparedToDayBefore()).isEqualTo(5.0);
    assertThat(captor.getValue().getTemperatureComparedToDayBefore()).isEqualTo(3.0);

    assertThat(result).isPresent();
    assertThat(result.get().humidity().comparedToDayBefore()).isEqualTo(5.0);
    assertThat(result.get().temperature().comparedToDayBefore()).isEqualTo(3.0);
  }

  @Test
  @DisplayName("기상청이 매핑 안 되는 하늘상태/강수형태 코드를 내려주면 저장하지 않고 건너뛴다")
  void skipsItemWithUnsupportedSkyStatusOrPrecipitationType() {
    // given: 매핑 안 되는 코드는 VilageFcstItem 생성 단계에서 이미 null로 들어옴
    VilageFcstItem unsupportedSky = item(LocalDateTime.of(2026, 7, 30, 9, 0), null, PrecipitationType.NONE);
    VilageFcstItem unsupportedPrecipitation = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, null);

    // when
    Optional<WeatherDto> resultForSky = weatherPersister.persist(unsupportedSky, grid, location);
    Optional<WeatherDto> resultForPrecipitation = weatherPersister.persist(unsupportedPrecipitation, grid, location);

    // then
    assertThat(resultForSky).isEmpty();
    assertThat(resultForPrecipitation).isEmpty();
    Mockito.verifyNoInteractions(weatherSaver);
  }

  @Test
  @DisplayName("이미 같은 시간대에 row가 있으면, upsert가 덮어쓴 결과를 그대로 응답으로 쓴다")
  void returnsUpsertedRowWhenSlotAlreadyExists() {
    // given: 예전 배치가 이미 이 시간대를 CLOUDY로 저장해뒀고, 이번 배치는 CLEAR로 다시 예측하는 상황.
    // upsert가 그 기존 row를 덮어쓴 결과(응답 DTO 기준으론 CLOUDY가 아니라 최신값)를 리턴한다고 가정하면,
    // persist()가 그 리턴값을 그대로 쓰는지(직접 새로 만든 Weather를 쓰는 게 아니라) 확인할 수 있다.
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    given(weatherRepository.findByGridAndForecastAt(grid, dayBeforeForecastAt))
        .willReturn(Optional.empty());

    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(KST).toInstant();
    Weather upserted = Weather.builder()
        .grid(grid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.MOSTLY_CLOUDY)
        .precipitationType(PrecipitationType.NONE)
        .build();
    given(weatherSaver.upsertInNewTransaction(any(Weather.class))).willReturn(upserted);

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().skyStatus()).isEqualTo(SkyStatus.MOSTLY_CLOUDY);
  }

  private Weather weatherWithTemperature(Instant forecastAt, Double current, Double min, Double max) {
    return Weather.builder()
        .grid(grid)
        .forecastedAt(forecastAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .temperatureCurrent(current)
        .temperatureMin(min)
        .temperatureMax(max)
        .build();
  }

  @Test
  @DisplayName("그 날짜에 예보가 하나도 없으면 아무것도 하지 않는다")
  void reconcileDailyMinMaxDoesNothingWhenNoForecastsForDate() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(List.of());

    // when
    weatherPersister.reconcileDailyMinMax(grid, date);

    // then
    Mockito.verify(weatherRepository, Mockito.never())
        .updateDailyTemperatureRange(any(), anyDouble(), anyDouble(), any(), any());
  }

  @Test
  @DisplayName("그 날짜 어딘가에 공식 TMN/TMX가 있으면 그 값을 그대로 신뢰해서 통일한다")
  void reconcileDailyMinMaxUsesOfficialValuesWhenPresent() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    List<Weather> dayForecasts = List.of(
        weatherWithTemperature(dayStart.plusSeconds(3600 * 6), 20.0, 18.0, null),
        weatherWithTemperature(dayStart.plusSeconds(3600 * 15), 26.0, null, 27.0),
        weatherWithTemperature(dayStart.plusSeconds(3600 * 20), 22.0, null, null)
    );
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(dayForecasts);

    // when
    weatherPersister.reconcileDailyMinMax(grid, date);

    // then
    verify(weatherRepository).updateDailyTemperatureRange(grid, 18.0, 27.0, dayStart, dayEnd);
  }

  @Test
  @DisplayName("공식 TMN/TMX가 하나도 없으면 그 날짜 전체 temperature 값으로 직접 계산한다")
  void reconcileDailyMinMaxComputesFromCurrentTemperatureWhenNoOfficialValues() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    List<Weather> dayForecasts = List.of(
        weatherWithTemperature(dayStart.plusSeconds(3600 * 6), 15.0, null, null),
        weatherWithTemperature(dayStart.plusSeconds(3600 * 15), 25.0, null, null),
        weatherWithTemperature(dayStart.plusSeconds(3600 * 20), 20.0, null, null)
    );
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(dayForecasts);

    // when
    weatherPersister.reconcileDailyMinMax(grid, date);

    // then
    verify(weatherRepository).updateDailyTemperatureRange(grid, 15.0, 25.0, dayStart, dayEnd);
  }

  @Test
  @DisplayName("min만 공식값이 있으면 min은 공식값을, max는 temperature로 직접 계산해서 따로 채운다")
  void reconcileDailyMinMaxResolvesMinAndMaxIndependently() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    List<Weather> dayForecasts = List.of(
        weatherWithTemperature(dayStart.plusSeconds(3600 * 6), 16.0, 16.0, null),
        weatherWithTemperature(dayStart.plusSeconds(3600 * 15), 24.0, null, null)
    );
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(dayForecasts);

    // when
    weatherPersister.reconcileDailyMinMax(grid, date);

    // then
    verify(weatherRepository).updateDailyTemperatureRange(grid, 16.0, 24.0, dayStart, dayEnd);
  }

  @Test
  @DisplayName("유효한 기온 데이터가 하나도 없으면(전부 null) 아무것도 하지 않는다")
  void reconcileDailyMinMaxDoesNothingWhenNoValidTemperatureData() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    List<Weather> dayForecasts = List.of(
        weatherWithTemperature(dayStart.plusSeconds(3600 * 6), null, null, null)
    );
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(dayForecasts);

    // when
    weatherPersister.reconcileDailyMinMax(grid, date);

    // then
    Mockito.verify(weatherRepository, Mockito.never())
        .updateDailyTemperatureRange(any(), anyDouble(), anyDouble(), any(), any());
  }
}
