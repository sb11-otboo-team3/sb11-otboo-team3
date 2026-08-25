package com.otboo.domain.weather.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.util.ActiveGridFinder;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherPrefetchSchedulerTest {

  @Mock
  private ActiveGridFinder activeGridFinder;

  @Mock
  private WeatherPrefetchProducer weatherPrefetchProducer;

  @Mock
  private Clock clock;

  private SimpleMeterRegistry meterRegistry;
  private WeatherPrefetchScheduler weatherPrefetchScheduler;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    weatherPrefetchScheduler = new WeatherPrefetchScheduler(
        activeGridFinder, weatherPrefetchProducer, meterRegistry, clock);
  }

  private Grid gridOf(int x, int y) {
    return Grid.builder().x(x).y(y).build();
  }

  @Test
  @DisplayName("활성 격자마다 프리페치 요청 메시지를 발행한다")
  void publishesRequestForEachActiveGrid() {
    given(clock.instant()).willReturn(Instant.now(), Instant.now());
    given(activeGridFinder.findActiveGrids())
        .willReturn(List.of(gridOf(60, 127), gridOf(61, 128)));

    weatherPrefetchScheduler.publishActiveGrids();

    verify(weatherPrefetchProducer).send(new GridForecastRequestedMessage(60, 127));
    verify(weatherPrefetchProducer).send(new GridForecastRequestedMessage(61, 128));
  }

  @Test
  @DisplayName("일부 격자 발행이 실패해도 나머지는 계속 발행하고, 성공/실패 건수를 카운터로 기록한다")
  void recordsPublishedAndFailedCounters_whenSomeGridsFail() {
    given(clock.instant()).willReturn(Instant.now(), Instant.now());
    given(activeGridFinder.findActiveGrids())
        .willReturn(List.of(gridOf(60, 127), gridOf(61, 128), gridOf(62, 129)));
    // 나머지 두 격자는 stub 안 된 인자로 호출되는데, strict stub 모드에서 "다른 인자로 이미 stub된
    // 메서드를 호출했다"고 오탐(PotentialStubbingProblem)하는 걸 막기 위해 lenient 처리.
    lenient().doThrow(new IllegalStateException("발행 실패"))
        .when(weatherPrefetchProducer).send(new GridForecastRequestedMessage(61, 128));

    weatherPrefetchScheduler.publishActiveGrids();

    verify(weatherPrefetchProducer, times(3)).send(org.mockito.ArgumentMatchers.any());
    assertThat(meterRegistry.counter("weather.prefetch.publish.count").count()).isEqualTo(2.0);
    assertThat(meterRegistry.counter("weather.prefetch.publish.failed").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("활성 격자가 없으면 아무것도 발행하지 않고 카운터는 0으로 기록한다")
  void noActiveGrids_publishesNothing() {
    given(clock.instant()).willReturn(Instant.now(), Instant.now());
    given(activeGridFinder.findActiveGrids()).willReturn(List.of());

    weatherPrefetchScheduler.publishActiveGrids();

    verify(weatherPrefetchProducer, times(0)).send(org.mockito.ArgumentMatchers.any());
    assertThat(meterRegistry.counter("weather.prefetch.publish.count").count()).isZero();
    assertThat(meterRegistry.counter("weather.prefetch.publish.failed").count()).isZero();
  }

  @Test
  @DisplayName("전체 발행 소요시간을 타이머로 기록한다")
  void recordsPublishDurationTimer() {
    Instant start = Instant.parse("2026-08-25T02:15:00Z");
    Instant end = Instant.parse("2026-08-25T02:15:07Z");
    given(clock.instant()).willReturn(start, end);
    given(activeGridFinder.findActiveGrids()).willReturn(List.of(gridOf(60, 127)));

    weatherPrefetchScheduler.publishActiveGrids();

    Timer timer = meterRegistry.find("weather.prefetch.publish.duration").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(7.0);
  }
}
