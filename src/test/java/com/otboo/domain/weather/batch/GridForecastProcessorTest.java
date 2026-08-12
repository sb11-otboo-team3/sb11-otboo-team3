package com.otboo.domain.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

// grid 하나를 기상청 조회 결과로 바꾸는 ItemProcessor. KMA 실패(KmaApiException)는 여기서 잡지 않고
// 그대로 던진다 - Step에 붙는 skip 정책이 처리하도록 위임하는 설계이기 때문에, 그 위임이 실제로 되는지도 검증한다.
@ExtendWith(MockitoExtension.class)
class GridForecastProcessorTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private KmaWeatherClient kmaWeatherClient;

  @Mock
  private VilageFcstBaseTimeResolver baseTimeResolver;

  private GridForecastProcessor processor;

  private final Grid grid = Grid.builder().x(60).y(127).build();
  private final Clock clock = Clock.fixed(
      LocalDateTime.of(2026, 8, 10, 9, 20).atZone(KST).toInstant(), KST);
  private final VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 8, 10), LocalTime.of(8, 0));

  @BeforeEach
  void setUp() {
    processor = new GridForecastProcessor(kmaWeatherClient, baseTimeResolver, clock);
    given(baseTimeResolver.resolve(LocalDateTime.now(clock))).willReturn(baseTime);
  }

  private VilageFcstItem item(LocalDateTime forecastAt) {
    return new VilageFcstItem(
        baseTime.baseDate().atTime(baseTime.baseTime()),
        forecastAt, SkyStatus.CLEAR, PrecipitationType.NONE,
        0.0, 20.0, 55.0, 23.0, null, null, 2.3
    );
  }

  @Test
  @DisplayName("기상청이 항목을 내려주면 grid와 항목들을 담은 GridForecast를 리턴한다")
  void returnsGridForecastWhenKmaReturnsItems() {
    // given
    List<VilageFcstItem> items = List.of(item(LocalDateTime.of(2026, 8, 10, 12, 0)));
    given(kmaWeatherClient.getForecast(grid.getX(), grid.getY(), baseTime))
        .willReturn(Mono.just(items));

    // when
    GridForecast result = processor.process(grid);

    // then
    assertThat(result).isNotNull();
    assertThat(result.grid()).isEqualTo(grid);
    assertThat(result.items()).isEqualTo(items);
  }

  @Test
  @DisplayName("기상청이 빈 리스트를 내려주면 null을 리턴해서(필터) 쓰기 단계로 안 넘어간다")
  void returnsNullWhenKmaReturnsEmptyList() {
    // given
    given(kmaWeatherClient.getForecast(grid.getX(), grid.getY(), baseTime))
        .willReturn(Mono.just(List.of()));

    // when
    GridForecast result = processor.process(grid);

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("기상청 호출이 실패하면 여기서 잡지 않고 그대로 전파한다(Step의 skip 정책이 처리)")
  void propagatesKmaApiExceptionForSkipPolicyToHandle() {
    // given
    given(kmaWeatherClient.getForecast(grid.getX(), grid.getY(), baseTime))
        .willReturn(Mono.error(new KmaApiException(grid.getX(), grid.getY(), baseTime, null)));

    // when & then
    assertThatThrownBy(() -> processor.process(grid))
        .isInstanceOf(KmaApiException.class);
  }
}
