package com.otboo.domain.weather.batch;

import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

// 격자 하나를 기상청 조회 결과로 바꾼다. 온디맨드 흐름(WeatherForecastFinder)과 마찬가지로 KMA 호출
// 실패(KmaApiException)를 여기서 잡지 않고 그대로 던진다 - WeatherPrefetchJobConfig의 Step에 붙는
// skip 정책이 처리하도록 위임해서, 격자 하나 실패가 나머지 격자 처리를 막지 않게 한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class GridForecastProcessor implements ItemProcessor<Grid, GridForecast> {

  private final KmaWeatherClient kmaWeatherClient;
  private final VilageFcstBaseTimeResolver baseTimeResolver;
  private final Clock clock;

  @Override
  public GridForecast process(Grid grid) {
    VilageFcstBaseTime baseTime = baseTimeResolver.resolve(LocalDateTime.now(clock));
    List<VilageFcstItem> items = kmaWeatherClient.getForecast(grid.getX(), grid.getY(), baseTime).block();

    if (items == null || items.isEmpty()) {
      log.warn("프리패치 - 기상청 응답에 항목 없음, grid=({},{}), baseTime={}", grid.getX(), grid.getY(), baseTime);
      // ItemProcessor가 null을 리턴하면 이 아이템은 필터링되어 ItemWriter로 안 넘어간다(Spring Batch 표준 동작).
      return null;
    }

    return new GridForecast(grid, items);
  }
}
