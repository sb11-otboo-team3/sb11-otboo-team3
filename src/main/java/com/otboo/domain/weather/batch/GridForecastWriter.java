package com.otboo.domain.weather.batch;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.service.WeatherPersister;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

// GridForecastProcessor가 만든 grid+항목들을 실제로 저장한다. 온디맨드 흐름(WeatherForecastFinder)과
// 동일하게, 항목 하나하나를 저장하는 것에 더해 그 항목들이 걸치는 날짜마다 min/max도 재계산해서 반영한다 -
// 안 그러면 프리패치로 채워진 데이터가 일 최저/최고기온 없이 남아있게 된다.
@Component
@RequiredArgsConstructor
public class GridForecastWriter implements ItemWriter<GridForecast> {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherPersister weatherPersister;

  @Override
  public void write(Chunk<? extends GridForecast> chunk) {
    for (GridForecast forecast : chunk) {
      persist(forecast);
    }
  }

  private void persist(GridForecast forecast) {
    Grid grid = forecast.grid();
    Set<LocalDate> dates = new HashSet<>();

    for (VilageFcstItem item : forecast.items()) {
      weatherPersister.persist(item, grid);
      dates.add(item.forecastAt().atZone(KST).toLocalDate());
    }

    for (LocalDate date : dates) {
      weatherPersister.resolveDailyMinMax(grid, date)
          .ifPresent(range -> weatherPersister.persistDailyMinMax(grid, date, range.min(), range.max()));
    }
  }
}
