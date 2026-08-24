package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.ActiveGridFinder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeatherDailyDiffSchedulerTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private ActiveGridFinder activeGridFinder;

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private final WeatherDiffEvaluator weatherDiffEvaluator = new WeatherDiffEvaluator();
  private final WeatherDiffProperties weatherDiffProperties = new WeatherDiffProperties(5.0, 3.0);
  private final Clock clock = Clock.fixed(
      LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant(), KST);

  private WeatherDailyDiffScheduler scheduler;

  // 실제로는 DB에서 읽어와 id가 항상 채워져 있다(WeatherDailyDiffScheduler가 grid.getId()로 묶음) -
  // builder만 쓰면 id가 null이라 groupingBy(Weather::getGrid.getId())가 NPE로 터진다.
  private final Grid grid = gridWithId(60, 127);
  // clock이 07:00으로 고정돼 있어 자정(00:00)보다 늦으므로, 스케줄러가 실제로 쓰는 하한은
  // 자정이 아니라 지금(clock.instant()) - 이미 지난 시간대는 조회 대상에서 빠진다.
  private final Instant queryStart = clock.instant();
  private final Instant dayEnd = LocalDateTime.of(2026, 7, 31, 0, 0).atZone(KST).toInstant();

  @BeforeEach
  void setUp() {
    scheduler = new WeatherDailyDiffScheduler(
        activeGridFinder, weatherRepository, weatherDiffEvaluator, weatherDiffProperties, eventPublisher, clock);
  }

  private static Grid gridWithId(int x, int y) {
    Grid grid = Grid.builder().x(x).y(y).build();
    ReflectionTestUtils.setField(grid, "id", UUID.randomUUID());
    return grid;
  }

  private Weather slot(Grid grid, Instant forecastAt, double temperature, PrecipitationType precipitationType, double windSpeed) {
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
    given(activeGridFinder.findActiveGrids()).willReturn(List.of());

    // when
    scheduler.run();

    // then
    Mockito.verifyNoInteractions(eventPublisher);
    Mockito.verifyNoInteractions(weatherRepository);
  }

  @Test
  @DisplayName("격자에 오늘자 급변이 감지되면 이벤트를 발행한다")
  void publishesEventWhenGridHasTriggeredDiff() {
    // given: 07시 20도 -> 08시 24도, 1시간에 4도(≥3°C/h)
    given(activeGridFinder.findActiveGrids()).willReturn(List.of(grid));
    given(weatherRepository.findByGridInAndForecastAtGreaterThanEqualAndForecastAtLessThan(List.of(grid), queryStart, dayEnd))
        .willReturn(List.of(
            slot(grid, LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(grid, LocalDateTime.of(2026, 7, 30, 8, 0).atZone(KST).toInstant(), 24.0, PrecipitationType.NONE, 2.0)
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
    // given: 07시 20도 -> 08시 21도, 급변 아님
    given(activeGridFinder.findActiveGrids()).willReturn(List.of(grid));
    given(weatherRepository.findByGridInAndForecastAtGreaterThanEqualAndForecastAtLessThan(List.of(grid), queryStart, dayEnd))
        .willReturn(List.of(
            slot(grid, LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(grid, LocalDateTime.of(2026, 7, 30, 8, 0).atZone(KST).toInstant(), 21.0, PrecipitationType.NONE, 2.0)
        ));

    // when
    scheduler.run();

    // then
    Mockito.verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("여러 격자 중 급변이 감지된 격자에 대해서만 이벤트를 발행한다")
  void publishesOnlyForTriggeredGridAmongMultiple() {
    // given: grid는 급변 있음, otherGrid는 없음 - 한 번의 IN절 조회로 두 격자 데이터를 같이 받아온다
    Grid otherGrid = gridWithId(61, 128);
    given(activeGridFinder.findActiveGrids()).willReturn(List.of(grid, otherGrid));
    given(weatherRepository.findByGridInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
        List.of(grid, otherGrid), queryStart, dayEnd))
        .willReturn(List.of(
            slot(grid, LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(grid, LocalDateTime.of(2026, 7, 30, 8, 0).atZone(KST).toInstant(), 24.0, PrecipitationType.NONE, 2.0),
            slot(otherGrid, LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant(), 20.0, PrecipitationType.NONE, 2.0),
            slot(otherGrid, LocalDateTime.of(2026, 7, 30, 8, 0).atZone(KST).toInstant(), 21.0, PrecipitationType.NONE, 2.0)
        ));

    // when
    scheduler.run();

    // then
    ArgumentCaptor<WeatherDailyDiffEvent> captor = ArgumentCaptor.forClass(WeatherDailyDiffEvent.class);
    verify(eventPublisher, times(1)).publishEvent(captor.capture());
    assertThat(captor.getValue().grid()).isEqualTo(grid);
  }

  @Test
  @DisplayName("자정 정각에 실행되면 지금이 자정보다 늦지 않으므로 하한이 자정 그대로다")
  void queriesFromMidnightWhenRunExactlyAtMidnight() {
    // given: clock을 자정으로 고정 - clock.instant()가 dayStart보다 늦지 않은 유일한 경우
    Clock midnightClock = Clock.fixed(LocalDateTime.of(2026, 7, 30, 0, 0).atZone(KST).toInstant(), KST);
    Instant midnightDayStart = LocalDateTime.of(2026, 7, 30, 0, 0).atZone(KST).toInstant();
    WeatherDailyDiffScheduler midnightScheduler = new WeatherDailyDiffScheduler(
        activeGridFinder, weatherRepository, weatherDiffEvaluator, weatherDiffProperties, eventPublisher, midnightClock);
    given(activeGridFinder.findActiveGrids()).willReturn(List.of(grid));
    given(weatherRepository.findByGridInAndForecastAtGreaterThanEqualAndForecastAtLessThan(List.of(grid), midnightDayStart, dayEnd))
        .willReturn(List.of());

    // when
    midnightScheduler.run();

    // then: 위 given()의 인자(자정)와 실제 호출 인자가 일치해야 스텁이 매칭되고 예외 없이 끝난다
    Mockito.verifyNoInteractions(eventPublisher);
  }
}
