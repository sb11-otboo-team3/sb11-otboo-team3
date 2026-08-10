package com.otboo.domain.weather.service;

import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 같은 (grid, forecastAt)에 이미 row가 있으면 최신 값으로 덮어쓰고, 없으면 새로 만든다(upsert).
// 별도 트랜잭션으로 분리해서 이 저장 하나가 바깥 트랜잭션과 독립적으로 즉시 커밋되게 한다.
@Component
@RequiredArgsConstructor
public class WeatherSaver {

  private final WeatherRepository weatherRepository;

  // weather.getId()는 쓰지 않는다 - 이 시점의 weather는 아직 저장 전(transient)이라 id가 없고,
  // upsert가 실제로 INSERT로 처리될 때만 여기서 새로 생성한 id가 쓰인다(이미 있던 row면 기존 id 유지).
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Weather upsertInNewTransaction(Weather weather) {
    Optional<Weather> upserted = weatherRepository.upsert(
        UUID.randomUUID(),
        weather.getGrid().getId(),
        weather.getForecastedAt(),
        weather.getForecastAt(),
        weather.getSkyStatus().name(),
        weather.getPrecipitationType().name(),
        weather.getPrecipitationAmount(),
        weather.getPrecipitationProbability(),
        weather.getHumidityCurrent(),
        weather.getHumidityComparedToDayBefore(),
        weather.getTemperatureCurrent(),
        weather.getTemperatureComparedToDayBefore(),
        weather.getTemperatureMin(),
        weather.getTemperatureMax(),
        weather.getWindSpeed()
    );

    // upsert가 비어있으면(WHERE 절에 걸려 스킵됨) 이 요청의 forecastedAt이 기존 row보다 과거라는 뜻 -
    // 이미 DB엔 더 최신 값이 있으니, 내가 만들려던 값 대신 그 현재 row를 그대로 반환한다.
    return upserted.orElseGet(() -> weatherRepository
        .findByGridAndForecastAt(weather.getGrid(), weather.getForecastAt())
        .orElseThrow(() -> new IllegalStateException(
            "upsert가 스킵됐는데(더 최신 forecastedAt 존재) 정작 그 row를 재조회하지 못함 - grid=%s, forecastAt=%s"
                .formatted(weather.getGrid().getId(), weather.getForecastAt()))));
  }
}
