package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.ActiveGridFinder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 일일별 급변: 발표별(WeatherPersister, 배치가 돌 때마다)과 달리 하루에 딱 한 번만 계산한다 -
// 그래서 배치 파이프라인 안이 아니라 완전히 별도인 스케줄러로 뺐다. 05시 발표가 05시15분에 이미
// DB에 저장돼 있으니, 07시에 그 데이터를 그대로 다시 읽기만 하면 된다(추가 조회/계산 불필요).
@Component
@RequiredArgsConstructor
public class WeatherDailyDiffScheduler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ActiveGridFinder activeGridFinder;
  private final WeatherRepository weatherRepository;
  private final WeatherDiffEvaluator weatherDiffEvaluator;
  private final WeatherDiffProperties weatherDiffProperties;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Scheduled(cron = "${weather.daily-diff.cron:0 0 7 * * *}", zone = "Asia/Seoul")
  @SchedulerLock(name = "weatherDailyDiffScheduler", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
  public void run() {
    List<Grid> activeGrids = activeGridFinder.findActiveGrids();
    if (activeGrids.isEmpty()) {
      return;
    }

    LocalDate today = LocalDate.now(clock.withZone(KST));
    Instant dayStart = today.atStartOfDay(KST).toInstant();
    Instant dayEnd = today.plusDays(1).atStartOfDay(KST).toInstant();
    // 이미 지난 시간대는 볼 필요 없다 - 이 알림은 "앞으로 대비하라"는 목적이라, 자정~지금 사이에
    // 있었던 급변을 지금 와서 알려주는 건 의미가 없다(스케줄을 자주 돌릴수록 이 문제가 두드러짐).
    Instant queryStart = clock.instant().isAfter(dayStart) ? clock.instant() : dayStart;

    // 격자마다 따로 조회하는 대신 IN절 하나로 묶는다. 같은 쿼리 결과 안의 Grid는 같은 영속성
    // 컨텍스트에서 나온 것이라 동일 인스턴스지만, activeGrids 쪽 Grid와는 세션이 다를 수 있어
    // 참조 동등성을 믿을 수 없다 - id(UUID)로 묶는다.
    Map<UUID, List<Weather>> weathersByGridId = weatherRepository
        .findByGridInAndForecastAtGreaterThanEqualAndForecastAtLessThan(activeGrids, queryStart, dayEnd)
        .stream()
        .collect(Collectors.groupingBy(weather -> weather.getGrid().getId()));

    for (Grid grid : activeGrids) {
      List<Weather> todaysWeather = weathersByGridId.getOrDefault(grid.getId(), List.of())
          .stream()
          .sorted(Comparator.comparing(Weather::getForecastAt))
          .toList();

      Set<DailyDiffTrigger> triggeredCategories = weatherDiffEvaluator.evaluateDailyDiff(todaysWeather, weatherDiffProperties);
      if (!triggeredCategories.isEmpty()) {
        eventPublisher.publishEvent(new WeatherDailyDiffEvent(grid, today, triggeredCategories));
      }
    }
  }
}
