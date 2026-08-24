package com.otboo.domain.weather.util;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// "활성 격자"(최근에 실제로 요청된 격자) 판정 기준. 프리패치 배치(WeatherPrefetchJobConfig)와
// 일일별 diff 스케줄러(WeatherDailyDiffScheduler)가 같은 기준을 써야 두 컴포넌트가 항상 같은
// 그리드 집합을 보게 된다 - 따로 정의하면 한쪽만 조정했을 때 서로 어긋난다.
@Component
@RequiredArgsConstructor
public class ActiveGridFinder {

  private static final Duration ACTIVE_WINDOW = Duration.ofDays(3);

  private final GridRepository gridRepository;
  private final Clock clock;

  public List<Grid> findActiveGrids() {
    Instant threshold = clock.instant().minus(ACTIVE_WINDOW);
    return gridRepository.findByLastRequestedAtAfter(threshold);
  }
}
