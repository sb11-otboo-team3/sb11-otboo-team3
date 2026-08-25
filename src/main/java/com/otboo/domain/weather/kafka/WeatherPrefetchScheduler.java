package com.otboo.domain.weather.kafka;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.util.ActiveGridFinder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 활성 격자마다 프리페치 요청 메시지를 카프카로 발행한다. 기상청 호출/저장은 더 이상 여기서 하지 않고
// WeatherPrefetchConsumer가 메시지를 받아서 처리한다 - 이 클래스는 "발행"만 책임진다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherPrefetchScheduler {

  private static final String PUBLISHED_COUNTER = "weather.prefetch.publish.count";
  private static final String PUBLISH_FAILED_COUNTER = "weather.prefetch.publish.failed";
  private static final String PUBLISH_DURATION_TIMER = "weather.prefetch.publish.duration";
  private static final String ACTIVE_GRID_COUNT_GAUGE = "weather.prefetch.active.grid.count";

  private final ActiveGridFinder activeGridFinder;
  private final WeatherPrefetchProducer weatherPrefetchProducer;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  // 누적 카운터가 아니라 "가장 최근 실행 시점" 값이라 Gauge로 발행한다 - WeatherPrefetchMetricsListener의
  // 실패율 게이지와 같은 이유(예전 배치 정리 참고).
  private final AtomicInteger activeGridCount = new AtomicInteger(0);

  @PostConstruct
  public void registerActiveGridCountGauge() {
    Gauge.builder(ACTIVE_GRID_COUNT_GAUGE, activeGridCount, AtomicInteger::get)
        .description("가장 최근 프리페치 실행 시점의 활성 격자 수")
        .register(meterRegistry);
  }

  // 기상청 발표 시각(02,05,08,11,14,17,20,23시) + 15분마다 실행. zone을 명시해서 배포 환경의 서버 기본
  // TZ가 뭐든 항상 한국 시각 기준 발표+15분에 돌게 한다.
  @Scheduled(cron = "${weather.prefetch.cron:0 15 2,5,8,11,14,17,20,23 * * *}", zone = "Asia/Seoul")
  // 서버 2대라 cron이 두 인스턴스에서 동시에 발화할 수 있음 - ShedLock으로 한 인스턴스만 실제로 발행하게 함.
  @SchedulerLock(name = "weatherPrefetchJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
  public void publishActiveGrids() {
    Instant start = clock.instant();
    List<Grid> activeGrids = activeGridFinder.findActiveGrids();
    activeGridCount.set(activeGrids.size());
    log.info("날씨 프리페치 발행 대상 활성 격자 수: {}", activeGrids.size());

    long published = 0;
    long failed = 0;
    for (Grid grid : activeGrids) {
      try {
        weatherPrefetchProducer.send(new GridForecastRequestedMessage(grid.getX(), grid.getY()));
        published++;
      } catch (Exception exception) {
        failed++;
        // 이 격자 하나 발행 실패로 전체 루프가 멈추면 안 됨 - 나머지 격자는 계속 발행.
        log.error("날씨 프리페치 - 격자 발행 실패, grid=({},{})", grid.getX(), grid.getY(), exception);
      }
    }

    meterRegistry.counter(PUBLISHED_COUNTER).increment(published);
    meterRegistry.counter(PUBLISH_FAILED_COUNTER).increment(failed);
    meterRegistry.timer(PUBLISH_DURATION_TIMER).record(Duration.between(start, clock.instant()));

    log.info("날씨 프리페치 발행 완료 - 성공={}, 실패={}", published, failed);
  }
}
