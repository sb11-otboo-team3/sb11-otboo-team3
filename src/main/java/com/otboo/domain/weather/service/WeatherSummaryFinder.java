package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.WeatherForecastCache;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.exception.DailyForecastNotFoundException;
import com.otboo.domain.weather.exception.WeatherNotFoundException;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 날씨 요약(하늘상태/강수/기온) 조회. WeatherController가 아니라 Feed 도메인이 피드 생성 시점에 필요해서
// 쓰는 기능이라 - WeatherService(웨더 자신의 API 계약)에 억지로 끼워넣지 않고 독립된 컴포넌트로 분리함.
@Component
@RequiredArgsConstructor
public class WeatherSummaryFinder {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final WeatherForecastCache weatherForecastCache;

  public WeatherSummaryDto find(UUID weatherId) {
    Weather weather = weatherRepository.findById(weatherId)
        .orElseThrow(() -> new WeatherNotFoundException(weatherId));

    LocalDate date = weather.getForecastAt().atZone(KST).toLocalDate();
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    WeatherGrid weatherGrid = new WeatherGrid(weather.getGrid().getX(), weather.getGrid().getY());

    // 캐시엔 이 발표의 날짜별 대표 예보 전체가 한 덩어리로 들어있어서, 그 안에서 이 weather의 날짜 하나만 걸러낸다.
    TemperatureRange range = weatherForecastCache.find(weatherGrid, weather.getForecastedAt())
        .flatMap(cached -> cached.stream()
            .filter(dto -> dto.forecastAt().atZone(KST).toLocalDate().equals(date))
            .findFirst())
        .map(cached -> new TemperatureRange(cached.temperature().min(), cached.temperature().max()))
        .orElseGet(() -> dailyTemperatureRangeFromDb(weather.getGrid(), dayStart, dayEnd));

    return weather.toSummaryDto(range.min(), range.max());
  }

  private record TemperatureRange(double min, double max) {
  }

  // 캐시가 만료됐을 때(TTL 3시간 지남) DB로 폴백. forecastedAt(특정 배치)로 좁히지 않고 grid+날짜
  // 범위로만 조회한다 - upsert 구조상 시간대별로 row가 하나씩만 있어서, 이렇게 해야 오늘처럼 여러 배치에
  // 걸쳐 저장된 하루치를 전부 모을 수 있다(WeatherPersister.reconcileDailyMinMax 참고, 보통은 이미
  // 그쪽에서 min/max가 통일돼 있어서 여기선 안전망 성격에 가깝다).
  private TemperatureRange dailyTemperatureRangeFromDb(Grid grid, Instant dayStart, Instant dayEnd) {
    List<Weather> dayForecasts = weatherRepository
        .findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, dayStart, dayEnd);

    OptionalDouble min = dayForecasts.stream()
        .map(w -> w.getTemperatureMin() != null ? w.getTemperatureMin() : w.getTemperatureCurrent())
        .filter(Objects::nonNull)
        .mapToDouble(Double::doubleValue)
        .min();
    OptionalDouble max = dayForecasts.stream()
        .map(w -> w.getTemperatureMax() != null ? w.getTemperatureMax() : w.getTemperatureCurrent())
        .filter(Objects::nonNull)
        .mapToDouble(Double::doubleValue)
        .max();

    if (min.isEmpty() || max.isEmpty()) {
      throw noDailyForecastsFound(dayStart);
    }
    return new TemperatureRange(min.getAsDouble(), max.getAsDouble());
  }

  // grid+날짜 범위에 유효한 기온 데이터가 하나도 없는, 정상적으로는 있을 수 없는 상태
  private DailyForecastNotFoundException noDailyForecastsFound(Instant dayStart) {
    return new DailyForecastNotFoundException(dayStart);
  }
}
