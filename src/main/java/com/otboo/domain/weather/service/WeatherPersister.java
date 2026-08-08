package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 기상청 응답 항목 하나를 저장하고 응답 DTO로 변환한다. 온디맨드 조회 흐름(WeatherForecastFinder)과
// 나중에 붙을 배치가 똑같이 재사용할 수 있도록 저장 로직만 따로 뺀 것.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherPersister {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final WeatherSaver weatherSaver;

  public Optional<WeatherDto> persist(VilageFcstItem item, Grid grid, WeatherAPILocation location) {
    Instant forecastAt = item.forecastAt().atZone(KST).toInstant();
    Instant forecastedAt = item.forecastedAt().atZone(KST).toInstant();

    // 기상청이 매핑 안 되는(혹은 응답에 아예 없는) 상태 코드를 내려주면 sky_status/precipitation_type이
    // null이 되는데, 두 컬럼 다 NOT NULL이라 그대로 저장하면 DataIntegrityViolationException.
    // 임의로 기본값을 지어내는 대신 이 시간대 항목만 건너뛴다.
    if (item.skyStatus() == null || item.precipitationType() == null) {
      log.warn("미지원 기상청 상태 코드 - 예보 항목 스킵, grid=({},{}), forecastAt={}, forecastedAt={}, skyStatus={}, precipitationType={}",
          grid.getX(), grid.getY(), forecastAt, forecastedAt, item.skyStatus(), item.precipitationType());
      return Optional.empty();
    }

    Double humidityComparedToDayBefore = null;
    Double temperatureComparedToDayBefore = null;
    Optional<Weather> dayBefore = weatherRepository.findFirstByGridAndForecastAtOrderByForecastedAtDesc(
        grid, forecastAt.minus(1, ChronoUnit.DAYS));
    if (dayBefore.isPresent()) {
      Double humidityDayBefore = dayBefore.get().getHumidityCurrent();
      Double temperatureDayBefore = dayBefore.get().getTemperatureCurrent();
      if (item.humidity() != null && humidityDayBefore != null) {
        humidityComparedToDayBefore = item.humidity() - humidityDayBefore;
      }
      if (item.temperature() != null && temperatureDayBefore != null) {
        temperatureComparedToDayBefore = item.temperature() - temperatureDayBefore;
      }
    }

    Weather weather = Weather.builder()
        .grid(grid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(item.skyStatus())
        .precipitationType(item.precipitationType())
        .precipitationAmount(item.precipitationAmount())
        .precipitationProbability(item.precipitationProbability())
        .humidityCurrent(item.humidity())
        .humidityComparedToDayBefore(humidityComparedToDayBefore)
        .temperatureCurrent(item.temperature())
        .temperatureComparedToDayBefore(temperatureComparedToDayBefore)
        .temperatureMin(item.temperatureMin())
        .temperatureMax(item.temperatureMax())
        .windSpeed(item.windSpeed())
        .build();

    // REQUIRES_NEW로 분리된 저장 시도가 유니크 제약 위반으로 실패해도, 그 실패는 별도 트랜잭션 안에서
    // 끝나므로 여기서 잡아도 이 메서드의 트랜잭션(바깥)엔 영향 없다.
    try {
      weatherSaver.saveInNewTransaction(weather);
    } catch (DataIntegrityViolationException e) {
      log.warn("날씨 저장 - 동시성 충돌 발생, grid={}, forecastAt={}, forecastedAt={}",
          grid.getId(), forecastAt, forecastedAt, e);
      // 이 스레드는 저장에 실패했으니 다시 조회해서 id를 채워준다.
      return Optional.of(
          weatherRepository.findByGridAndForecastAtAndForecastedAt(grid, forecastAt, forecastedAt)
              .map(existing -> existing.toDto(location))
              .orElseGet(() -> item.toDto(location))
      );
    }

    return Optional.of(weather.toDto(location));
  }
}
