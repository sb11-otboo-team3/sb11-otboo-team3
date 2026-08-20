package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WeatherDailyDiffSchedulerTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private GridRepository gridRepository;

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private final WeatherDiffEvaluator weatherDiffEvaluator = new WeatherDiffEvaluator();
  private final WeatherDiffProperties weatherDiffProperties = new WeatherDiffProperties(5.0, 3.0);
  private final Clock clock = Clock.fixed(
      LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant(), KST);

  private WeatherDailyDiffScheduler scheduler;

  private final Grid grid = Grid.builder().x(60).y(127).build();
  private final Instant activeThreshold = clock.instant().minus(Duration.ofDays(3));
  private final Instant dayStart = LocalDateTime.of(2026, 7, 30, 0, 0).atZone(KST).toInstant();
  private final Instant dayEnd = LocalDateTime.of(2026, 7, 31, 0, 0).atZone(KST).toInstant();

  @BeforeEach
  void setUp() {
    scheduler = new WeatherDailyDiffScheduler(
        gridRepository, weatherRepository, weatherDiffEvaluator, weatherDiffProperties, eventPublisher, clock);
  }

  private Weather slot(Instant forecastAt, double temperature, PrecipitationType precipitationType, double windSpeed) {
    return Weather.builder()
        .grid(grid)
        .forecastedAt(forecastAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(precipitationType)
        .temperatureCurrent(temperature)
        .windSpeed(windSpeed)
        .build();
  }

  @Test
  @DisplayName("활성 격자가 없으면 아무 이벤트도 발행하지 않는다")
  void publishesNothingWhenNoActiveGrids() {
    // given
    given(gridRepository.findByLastRequestedAtAfter(activeThreshold)).willReturn(List.of());

    // when
    scheduler.run();

    // then
    Mockito.verifyNoInteractions(eventPublisher);
    Mockito.verifyNoInteractions(weatherRepository);
  }

  @Test
  @DisplayName("격자에 오늘자 급변이 감지되면 이벤트를 발행한다")
  void publishesEventWhenGridHasTriggeredDiff() {
    // given: 00시 20도 -> 01시 24도, 1시간에 4도(≥3°C/h)
    given(gridRepository.findByLastRequestedAtAfter(activeThreshold)).willReturn(List.of(grid));
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(List.of(
            slot(LocalDateTime.of(2026, 7, 30, 0, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(LocalDateTime.of(2026, 7, 30, 1, 0).atZone(KST).toInstant(), 24.0, PrecipitationType.NONE, 2.0)
        ));

    // when
    scheduler.run();

    // then
    ArgumentCaptor<WeatherDailyDiffEvent> captor = ArgumentCaptor.forClass(WeatherDailyDiffEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().grid()).isEqualTo(grid);
    assertThat(captor.getValue().triggeredCategories())
        .extracting(DailyDiffTrigger::category)
        .containsExactly(DiffCategory.TEMPERATURE);
  }

  @Test
  @DisplayName("격자에 오늘자 급변이 없으면 이벤트를 발행하지 않는다")
  void publishesNothingWhenGridHasNoTriggeredDiff() {
    // given: 00시 20도 -> 01시 21도, 급변 아님
    given(gridRepository.findByLastRequestedAtAfter(activeThreshold)).willReturn(List.of(grid));
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(List.of(
            slot(LocalDateTime.of(2026, 7, 30, 0, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(LocalDateTime.of(2026, 7, 30, 1, 0).atZone(KST).toInstant(), 21.0, PrecipitationType.NONE, 2.0)
        ));

    // when
    scheduler.run();

    // then
    Mockito.verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("여러 격자 중 급변이 감지된 격자에 대해서만 이벤트를 발행한다")
  void publishesOnlyForTriggeredGridAmongMultiple() {
    // given: grid는 급변 있음, otherGrid는 없음
    Grid otherGrid = Grid.builder().x(61).y(128).build();
    given(gridRepository.findByLastRequestedAtAfter(activeThreshold)).willReturn(List.of(grid, otherGrid));
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd))
        .willReturn(List.of(
            slot(LocalDateTime.of(2026, 7, 30, 0, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(LocalDateTime.of(2026, 7, 30, 1, 0).atZone(KST).toInstant(), 24.0, PrecipitationType.NONE, 2.0)
        ));
    given(weatherRepository.findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(otherGrid, dayStart, dayEnd))
        .willReturn(List.of(
            slot(LocalDateTime.of(2026, 7, 30, 0, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(LocalDateTime.of(2026, 7, 30, 1, 0).atZone(KST).toInstant(), 21.0, PrecipitationType.NONE, 2.0)
        ));

    // when
    scheduler.run();

    // then
    ArgumentCaptor<WeatherDailyDiffEvent> captor = ArgumentCaptor.forClass(WeatherDailyDiffEvent.class);
    verify(eventPublisher, times(1)).publishEvent(captor.capture());
    assertThat(captor.getValue().grid()).isEqualTo(grid);
  }
}
