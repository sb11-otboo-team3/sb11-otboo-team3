package com.otboo.domain.weather.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.service.WeatherPersister;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

// GridForecastProcessor가 만든 grid+항목들을 실제로 저장하는 ItemWriter.
// WeatherForecastFinder의 온디맨드 흐름과 동일하게, 항목 저장뿐 아니라 그 항목들이 걸치는 날짜마다
// min/max도 반영해야 한다는 게 핵심이라 그 부분을 집중적으로 검증한다.
@ExtendWith(MockitoExtension.class)
class GridForecastWriterTest {

  @Mock
  private WeatherPersister weatherPersister;

  private GridForecastWriter writer;

  private final Grid grid = Grid.builder().x(60).y(127).build();

  @BeforeEach
  void setUp() {
    writer = new GridForecastWriter(weatherPersister);
  }

  private VilageFcstItem item(LocalDateTime forecastAt) {
    return new VilageFcstItem(
        LocalDateTime.of(2026, 8, 10, 8, 0),
        forecastAt, SkyStatus.CLEAR, PrecipitationType.NONE,
        0.0, 20.0, 55.0, 23.0, null, null, 2.3
    );
  }

  @Test
  @DisplayName("GridForecast에 담긴 항목들을 전부 저장한다")
  void persistsAllItemsInGridForecast() throws Exception {
    // given
    VilageFcstItem item1 = item(LocalDateTime.of(2026, 8, 10, 9, 0));
    VilageFcstItem item2 = item(LocalDateTime.of(2026, 8, 10, 12, 0));
    given(weatherPersister.persist(any(VilageFcstItem.class), eq(grid))).willReturn(Optional.empty());
    given(weatherPersister.resolveDailyMinMax(eq(grid), any(LocalDate.class))).willReturn(Optional.empty());

    // when
    writer.write(new Chunk<>(List.of(new GridForecast(grid, List.of(item1, item2)))));

    // then
    verify(weatherPersister).persist(item1, grid);
    verify(weatherPersister).persist(item2, grid);
  }

  @Test
  @DisplayName("항목들이 걸치는 날짜마다 min/max를 계산해서 반영하되, 같은 날짜는 한 번만 계산한다")
  void resolvesAndPersistsDailyMinMaxOncePerDistinctDate() throws Exception {
    // given: 같은 날짜(8/10) 항목 2개 + 다른 날짜(8/11) 항목 1개
    VilageFcstItem sameDay1 = item(LocalDateTime.of(2026, 8, 10, 9, 0));
    VilageFcstItem sameDay2 = item(LocalDateTime.of(2026, 8, 10, 12, 0));
    VilageFcstItem otherDay = item(LocalDateTime.of(2026, 8, 11, 9, 0));
    given(weatherPersister.persist(any(VilageFcstItem.class), eq(grid))).willReturn(Optional.empty());
    given(weatherPersister.resolveDailyMinMax(grid, LocalDate.of(2026, 8, 10)))
        .willReturn(Optional.of(new WeatherPersister.DailyTemperatureRange(15.0, 25.0)));
    given(weatherPersister.resolveDailyMinMax(grid, LocalDate.of(2026, 8, 11)))
        .willReturn(Optional.of(new WeatherPersister.DailyTemperatureRange(16.0, 26.0)));

    // when
    writer.write(new Chunk<>(List.of(new GridForecast(grid, List.of(sameDay1, sameDay2, otherDay)))));

    // then
    verify(weatherPersister, times(1)).resolveDailyMinMax(grid, LocalDate.of(2026, 8, 10));
    verify(weatherPersister, times(1)).resolveDailyMinMax(grid, LocalDate.of(2026, 8, 11));
    verify(weatherPersister).persistDailyMinMax(grid, LocalDate.of(2026, 8, 10), 15.0, 25.0);
    verify(weatherPersister).persistDailyMinMax(grid, LocalDate.of(2026, 8, 11), 16.0, 26.0);
  }

  @Test
  @DisplayName("그 날짜에 유효한 min/max가 없으면 persistDailyMinMax는 호출하지 않는다")
  void doesNotPersistDailyMinMaxWhenResolveReturnsEmpty() throws Exception {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 8, 10, 9, 0));
    given(weatherPersister.persist(any(VilageFcstItem.class), eq(grid))).willReturn(Optional.empty());
    given(weatherPersister.resolveDailyMinMax(grid, LocalDate.of(2026, 8, 10))).willReturn(Optional.empty());

    // when
    writer.write(new Chunk<>(List.of(new GridForecast(grid, List.of(item)))));

    // then
    verify(weatherPersister, never()).persistDailyMinMax(any(), any(), anyDouble(), anyDouble());
  }

  @Test
  @DisplayName("청크에 여러 GridForecast가 있으면 각각 독립적으로 처리한다")
  void processesEachGridForecastInChunkIndependently() throws Exception {
    // given
    Grid otherGrid = Grid.builder().x(61).y(128).build();
    VilageFcstItem itemForGrid = item(LocalDateTime.of(2026, 8, 10, 9, 0));
    VilageFcstItem itemForOtherGrid = item(LocalDateTime.of(2026, 8, 10, 9, 0));
    given(weatherPersister.persist(any(VilageFcstItem.class), any(Grid.class))).willReturn(Optional.empty());
    given(weatherPersister.resolveDailyMinMax(any(Grid.class), any(LocalDate.class))).willReturn(Optional.empty());

    // when
    writer.write(new Chunk<>(List.of(
        new GridForecast(grid, List.of(itemForGrid)),
        new GridForecast(otherGrid, List.of(itemForOtherGrid))
    )));

    // then
    verify(weatherPersister).persist(itemForGrid, grid);
    verify(weatherPersister).persist(itemForOtherGrid, otherGrid);
  }
}
