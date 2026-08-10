package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    Optional<Weather> dayBefore = weatherRepository.findByGridAndForecastAt(
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

    // 같은 (grid, forecastAt)에 이미 row가 있으면(예: 예전 배치가 이미 이 시간대를 예측해놨으면) upsert가
    // 알아서 최신 값으로 덮어쓴다 - 유니크 위반을 신경 쓸 필요가 없어짐(WeatherSaver 참고).
    Weather saved = weatherSaver.upsertInNewTransaction(weather);
    return Optional.of(saved.toDto(location));
  }

  // 그 날짜(grid+date)의 min/max를 확정해서 그 날짜에 속한 모든 row에 통일해서 채운다. 기상청이 그 날짜
  // 어딘가에 실제 TMN/TMX를 실어줬으면 그 값을 그대로 신뢰하고, 하나도 없으면(배치 경계에 걸린 날 등)
  // 그 날짜 전체 row의 temperature(현재기온) 값들로 직접 계산한다. 이 시점에 grid+날짜로 다시 조회하니까
  // 방금 새로 저장한 row뿐 아니라 이전에 이미 저장돼 있던 row(예: 오늘 이미 지나간 시간대)까지 다 포함된다.
  public void reconcileDailyMinMax(Grid grid, LocalDate date) {
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();

    List<Weather> dayForecasts = weatherRepository
        .findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd);
    if (dayForecasts.isEmpty()) {
      return;
    }

    OptionalDouble min = resolvedExtreme(dayForecasts, Weather::getTemperatureMin, true);
    OptionalDouble max = resolvedExtreme(dayForecasts, Weather::getTemperatureMax, false);

    if (min.isEmpty() || max.isEmpty()) {
      log.warn("일 최저/최고기온 확정 실패 - 이 날짜에 유효한 기온 데이터가 하나도 없음, grid={}, date={}",
          grid.getId(), date);
      return;
    }

    weatherRepository.updateDailyTemperatureRange(grid, min.getAsDouble(), max.getAsDouble(), dayStart, dayEnd);
  }

  // 공식값(TMN/TMX)이 그 날짜 어딘가에 있으면 그걸 신뢰하고, 하나도 없으면 그 날짜 전체의
  // temperature(현재기온)로 직접 계산한다.
  private OptionalDouble resolvedExtreme(
      List<Weather> dayForecasts, Function<Weather, Double> officialValue, boolean isMin
  ) {
    OptionalDouble official = extreme(dayForecasts, officialValue, isMin);
    return official.isPresent() ? official : extreme(dayForecasts, Weather::getTemperatureCurrent, isMin);
  }

  private OptionalDouble extreme(List<Weather> dayForecasts, Function<Weather, Double> valueOf, boolean isMin) {
    DoubleStream values = dayForecasts.stream()
        .map(valueOf)
        .filter(Objects::nonNull)
        .mapToDouble(Double::doubleValue);
    return isMin ? values.min() : values.max();
  }
}
