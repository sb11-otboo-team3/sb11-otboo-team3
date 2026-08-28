package com.otboo.domain.weather.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.service.WeatherPersister;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// GridForecastRequestedMessage 하나(격자 하나)를 받아 기상청 조회 -> 저장 -> 일 min/max 반영까지 수행한다.
// 예전 배치의 GridForecastProcessor+GridForecastWriter가 하던 일을 그대로 옮긴 것. 실패(KmaApiException
// 포함)는 여기서 잡지 않고 그대로 던진다 - 공통 KafkaConsumerConfig의 재시도(1초 간격 2회)+DLT에 위임한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherPrefetchConsumer {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String COLLECTED_COUNTER = "weather.prefetch.grid.collected";

  private final GridRepository gridRepository;
  private final KmaWeatherClient kmaWeatherClient;
  private final VilageFcstBaseTimeResolver baseTimeResolver;
  private final WeatherPersister weatherPersister;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @KafkaListener(
      topics = WeatherKafkaTopics.GRID_FORECAST_REQUESTED,
      groupId = "otboo-weather-prefetch-consumer"
  )
  public void consume(String payload) {
    GridForecastRequestedMessage message = readMessage(payload);

    Grid grid = gridRepository.findByXAndY(message.x(), message.y())
        .orElseThrow(() -> new IllegalStateException(
            "프리페치 대상 격자를 찾을 수 없음, grid=(" + message.x() + "," + message.y() + ")"));

    VilageFcstBaseTime baseTime = baseTimeResolver.resolve(LocalDateTime.now(clock));
    List<VilageFcstItem> items = kmaWeatherClient.getForecast(grid.getX(), grid.getY(), baseTime).block();

    if (items == null || items.isEmpty()) {
      log.warn("프리패치 컨슈머 - 기상청 응답에 항목 없음, grid=({},{}), baseTime={}",
          grid.getX(), grid.getY(), baseTime);
      return;
    }

    persist(grid, items);
    meterRegistry.counter(COLLECTED_COUNTER).increment();
  }

  private void persist(Grid grid, List<VilageFcstItem> items) {
    Set<LocalDate> dates = new HashSet<>();

    for (VilageFcstItem item : items) {
      weatherPersister.persist(item, grid);
      dates.add(item.forecastAt().atZone(KST).toLocalDate());
    }

    for (LocalDate date : dates) {
      weatherPersister.resolveDailyMinMax(grid, date)
          .ifPresent(range -> weatherPersister.persistDailyMinMax(grid, date, range.min(), range.max()));
    }
  }

  private GridForecastRequestedMessage readMessage(String payload) {
    try {
      return objectMapper.readValue(payload, GridForecastRequestedMessage.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("날씨 프리페치 Kafka 메시지 역직렬화 실패", exception);
    }
  }
}
