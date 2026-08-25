package com.otboo.domain.weather.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 재처리는 하지 않는다 - 다음 발표시각(3시간 뒤)에 프로듀서가 같은 격자로 새 요청을 다시 발행하므로,
// DLT로 온 메시지는 재시도 대상이 아니라 "최종 실패했다"는 관측 신호일 뿐이다.
class WeatherPrefetchDeadLetterConsumerTest {

  private SimpleMeterRegistry meterRegistry;
  private WeatherPrefetchDeadLetterConsumer weatherPrefetchDeadLetterConsumer;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    weatherPrefetchDeadLetterConsumer = new WeatherPrefetchDeadLetterConsumer(meterRegistry);
  }

  @Test
  @DisplayName("DLT로 온 메시지를 실패 카운터로 기록한다")
  void consume_recordsFailedCounter() {
    weatherPrefetchDeadLetterConsumer.consume("{\"x\":60,\"y\":127}");

    assertThat(meterRegistry.counter("weather.prefetch.grid.failed").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("여러 건이 오면 누적해서 기록한다")
  void consume_accumulatesAcrossMultipleMessages() {
    weatherPrefetchDeadLetterConsumer.consume("{\"x\":60,\"y\":127}");
    weatherPrefetchDeadLetterConsumer.consume("{\"x\":61,\"y\":128}");

    assertThat(meterRegistry.counter("weather.prefetch.grid.failed").count()).isEqualTo(2.0);
  }
}
