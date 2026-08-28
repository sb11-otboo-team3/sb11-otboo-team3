package com.otboo.domain.weather.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.service.WeatherPersister;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WeatherPrefetchConsumerTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private GridRepository gridRepository;

  @Mock
  private KmaWeatherClient kmaWeatherClient;

  @Mock
  private VilageFcstBaseTimeResolver baseTimeResolver;

  @Mock
  private WeatherPersister weatherPersister;

  @Mock
  private Clock clock;

  private ObjectMapper objectMapper;
  private SimpleMeterRegistry meterRegistry;
  private WeatherPrefetchConsumer weatherPrefetchConsumer;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    meterRegistry = new SimpleMeterRegistry();
    weatherPrefetchConsumer = new WeatherPrefetchConsumer(
        gridRepository, kmaWeatherClient, baseTimeResolver, weatherPersister, meterRegistry,
        objectMapper, clock);

    // 격자를 못 찾거나 역직렬화에 실패하는 테스트는 이 지점까지 안 가서 안 쓰는 stub이 되므로 lenient.
    lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-25T02:00:00Z"));
    lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  private Grid gridOf(int x, int y) {
    return Grid.builder().x(x).y(y).build();
  }

  private VilageFcstItem itemAt(LocalDateTime forecastAt) {
    return new VilageFcstItem(
        LocalDateTime.of(2026, 8, 25, 2, 0), forecastAt,
        SkyStatus.CLEAR, PrecipitationType.NONE,
        0.0, 0.0, 50.0, 20.0, 18.0, 22.0, 3.0
    );
  }

  private String payloadOf(int x, int y) throws Exception {
    return objectMapper.writeValueAsString(new GridForecastRequestedMessage(x, y));
  }

  @Test
  @DisplayName("메시지를 받아 격자의 예보를 조회하고 저장한 뒤, 그 날짜의 일 최저/최고기온도 반영한다")
  void consume_success() throws Exception {
    Grid grid = gridOf(60, 127);
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 8, 25), LocalTime.of(2, 0));
    LocalDate forecastDate = LocalDate.of(2026, 8, 25);
    VilageFcstItem item1 = itemAt(LocalDateTime.of(2026, 8, 25, 6, 0));
    VilageFcstItem item2 = itemAt(LocalDateTime.of(2026, 8, 25, 9, 0));

    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(grid));
    given(baseTimeResolver.resolve(any(LocalDateTime.class))).willReturn(baseTime);
    given(kmaWeatherClient.getForecast(60, 127, baseTime))
        .willReturn(Mono.just(List.of(item1, item2)));
    given(weatherPersister.resolveDailyMinMax(grid, forecastDate))
        .willReturn(Optional.of(new WeatherPersister.DailyTemperatureRange(18.0, 22.0)));

    weatherPrefetchConsumer.consume(payloadOf(60, 127));

    verify(weatherPersister).persist(item1, grid);
    verify(weatherPersister).persist(item2, grid);
    // 두 항목이 같은 날짜라 min/max 반영은 날짜당 한 번만 일어난다.
    verify(weatherPersister).persistDailyMinMax(grid, forecastDate, 18.0, 22.0);
    assertThat(meterRegistry.counter("weather.prefetch.grid.collected").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("기상청 응답이 비어있으면 아무것도 저장하지 않고 수집 성공으로 세지도 않는다")
  void consume_emptyResponse_doesNothing() throws Exception {
    Grid grid = gridOf(60, 127);
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 8, 25), LocalTime.of(2, 0));

    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(grid));
    given(baseTimeResolver.resolve(any(LocalDateTime.class))).willReturn(baseTime);
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.just(List.of()));

    weatherPrefetchConsumer.consume(payloadOf(60, 127));

    verify(weatherPersister, never()).persist(any(), any());
    assertThat(meterRegistry.counter("weather.prefetch.grid.collected").count()).isZero();
  }

  @Test
  @DisplayName("기상청 응답 자체가 없으면(null) 아무것도 저장하지 않고 수집 성공으로 세지도 않는다")
  void consume_nullResponse_doesNothing() throws Exception {
    Grid grid = gridOf(60, 127);
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 8, 25), LocalTime.of(2, 0));

    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(grid));
    given(baseTimeResolver.resolve(any(LocalDateTime.class))).willReturn(baseTime);
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.empty());

    weatherPrefetchConsumer.consume(payloadOf(60, 127));

    verify(weatherPersister, never()).persist(any(), any());
    assertThat(meterRegistry.counter("weather.prefetch.grid.collected").count()).isZero();
  }

  @Test
  @DisplayName("메시지의 격자를 찾을 수 없으면 예외를 던진다")
  void consume_gridNotFound_throwsException() throws Exception {
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());

    assertThatThrownBy(() -> weatherPrefetchConsumer.consume(payloadOf(60, 127)))
        .isInstanceOf(IllegalStateException.class);

    verify(weatherPersister, never()).persist(any(), any());
  }

  @Test
  @DisplayName("기상청 호출이 실패하면 잡지 않고 그대로 전파한다(공통 Kafka 재시도/DLT에 위임)")
  void consume_kmaApiException_propagates() throws Exception {
    Grid grid = gridOf(60, 127);
    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 8, 25), LocalTime.of(2, 0));
    KmaApiException kmaApiException = new KmaApiException(60, 127, baseTime, new RuntimeException("timeout"));

    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(grid));
    given(baseTimeResolver.resolve(any(LocalDateTime.class))).willReturn(baseTime);
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.error(kmaApiException));

    assertThatThrownBy(() -> weatherPrefetchConsumer.consume(payloadOf(60, 127)))
        .isSameAs(kmaApiException);

    assertThat(meterRegistry.counter("weather.prefetch.grid.collected").count()).isZero();
  }

  @Test
  @DisplayName("메시지 역직렬화에 실패하면 예외를 던진다")
  void consume_invalidPayload_throwsException() {
    assertThatThrownBy(() -> weatherPrefetchConsumer.consume("invalid-json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("날씨 프리페치 Kafka 메시지 역직렬화 실패");
  }
}
