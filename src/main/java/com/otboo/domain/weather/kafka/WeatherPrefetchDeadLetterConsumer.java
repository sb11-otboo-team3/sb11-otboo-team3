package com.otboo.domain.weather.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// DLT(otboo.weather.prefetch-requested.v1.dlt)를 구독하는 순수 관측용 컴포넌트. 재처리는 하지 않는다 -
// 다음 발표시각(3시간 뒤)에 프로듀서가 같은 격자로 새 요청을 다시 발행하므로, DLT로 온 메시지는 재시도
// 대상이 아니라 "최종 실패했다"는 신호일 뿐이다. 일부러 페이로드를 파싱하지 않는다 - 여기서 예외가 나면
// 이 리스너 자체가 다시 재시도+DLT 대상이 되는 이상한 상황(.dlt.dlt)이 생길 수 있어서, 실패할 일이
// 없게 최대한 단순하게 유지한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherPrefetchDeadLetterConsumer {

  private static final String FAILED_COUNTER = "weather.prefetch.grid.failed";

  private final MeterRegistry meterRegistry;

  @KafkaListener(
      topics = WeatherKafkaTopics.GRID_FORECAST_REQUESTED_DLT,
      groupId = "otboo-weather-prefetch-dlt-consumer"
  )
  public void consume(String payload) {
    meterRegistry.counter(FAILED_COUNTER).increment();
    log.error("날씨 프리페치 - 최종 실패(재시도 소진), DLT로 이동한 메시지: {}", payload);
  }
}
