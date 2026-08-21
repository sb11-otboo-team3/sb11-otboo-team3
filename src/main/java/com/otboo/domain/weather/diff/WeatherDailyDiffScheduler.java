package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
  // 프리패치 배치(WeatherPrefetchJobConfig)와 같은 기준 - 실제로 조회되는 격자만 대상으로 한다.
  private static final Duration ACTIVE_WINDOW = Duration.ofDays(3);

  private final GridRepository gridRepository;
  private final WeatherRepository weatherRepository;
  private final WeatherDiffEvaluator weatherDiffEvaluator;
  private final WeatherDiffProperties weatherDiffProperties;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Scheduled(cron = "${weather.daily-diff.cron:0 0 7 * * *}", zone = "Asia/Seoul")
  @SchedulerLock(name = "weatherDailyDiffScheduler", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
  public void run() {
    Instant threshold = clock.instant().minus(ACTIVE_WINDOW);
    List<Grid> activeGrids = gridRepository.findByLastRequestedAtAfter(threshold);

    LocalDate today = LocalDate.now(clock.withZone(KST));
    Instant dayStart = today.atStartOfDay(KST).toInstant();
    Instant dayEnd = today.plusDays(1).atStartOfDay(KST).toInstant();
    // 이미 지난 시간대는 볼 필요 없다 - 이 알림은 "앞으로 대비하라"는 목적이라, 자정~지금 사이에
    // 있었던 급변을 지금 와서 알려주는 건 의미가 없다(스케줄을 자주 돌릴수록 이 문제가 두드러짐).
    Instant queryStart = clock.instant().isAfter(dayStart) ? clock.instant() : dayStart;

    for (Grid grid : activeGrids) {
      List<Weather> todaysWeather = weatherRepository
          .findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, queryStart, dayEnd)
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
