package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;

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
        2.3,
        WindStrength.WEAK
    );
  }

  @Test
  @DisplayName("전날 같은 시각 기록이 없으면 전일 대비 값 없이 저장한다")
  void savesWithoutComparedToDayBeforeWhenNoYesterdayRecord() {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    given(weatherRepository.findFirstByGridAndForecastAtOrderByForecastedAtDesc(grid, dayBeforeForecastAt))
        .willReturn(Optional.empty());

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().humidity().comparedToDayBefore()).isEqualTo(0.0);
    assertThat(result.get().temperature().comparedToDayBefore()).isEqualTo(0.0);

    ArgumentCaptor<Weather> captor = ArgumentCaptor.forClass(Weather.class);
    verify(weatherSaver).saveInNewTransaction(captor.capture());
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
    given(weatherRepository.findFirstByGridAndForecastAtOrderByForecastedAtDesc(grid, dayBeforeForecastAt))
        .willReturn(Optional.of(yesterday));

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    ArgumentCaptor<Weather> captor = ArgumentCaptor.forClass(Weather.class);
    verify(weatherSaver).saveInNewTransaction(captor.capture());
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
  @DisplayName("동시에 같은 예보가 먼저 저장돼 유니크 제약 위반이 나면, 재조회한 값으로 정상 반환한다")
  void returnsRefetchedDtoWhenConcurrentSaveConflicts() {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    given(weatherRepository.findFirstByGridAndForecastAtOrderByForecastedAtDesc(grid, dayBeforeForecastAt))
        .willReturn(Optional.empty());
    Mockito.doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(weatherSaver).saveInNewTransaction(any(Weather.class));

    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(KST).toInstant();
    Weather winner = Weather.builder()
        .grid(grid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .build();
    given(weatherRepository.findByGridAndForecastAtAndForecastedAt(grid, forecastAt, forecastedAt))
        .willReturn(Optional.of(winner));

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(winner.getId());
  }

  @Test
  @DisplayName("유니크 제약 위반 후 재조회에서도 못 찾으면, 저장 전 값 그대로 응답으로 대체한다")
  void fallsBackToItemDtoWhenConcurrentSaveConflictsAndRefetchAlsoMisses() {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    given(weatherRepository.findFirstByGridAndForecastAtOrderByForecastedAtDesc(grid, dayBeforeForecastAt))
        .willReturn(Optional.empty());
    Mockito.doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(weatherSaver).saveInNewTransaction(any(Weather.class));

    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(KST).toInstant();
    given(weatherRepository.findByGridAndForecastAtAndForecastedAt(grid, forecastAt, forecastedAt))
        .willReturn(Optional.empty());

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().id()).isNull();
    assertThat(result.get().skyStatus()).isEqualTo(SkyStatus.CLEAR);
  }
}
