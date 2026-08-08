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

    // 캐시가 날짜별로 쪼개져 있어서, 그 weather의 날짜 하나만 바로 조회하면 됨(전체 훑어 필터링 불필요).
    TemperatureRange range = weatherForecastCache.find(weatherGrid, weather.getForecastedAt(), date)
        .map(cached -> new TemperatureRange(cached.temperature().min(), cached.temperature().max()))
        .orElseGet(() -> dailyTemperatureRangeFromDb(weather.getGrid(), weather.getForecastedAt(), dayStart, dayEnd));

    return weather.toSummaryDto(range.min(), range.max());
  }

  private record TemperatureRange(double min, double max) {
  }

  // 캐시가 만료됐을 때(TTL 3시간 지남) DB로 폴백
  private TemperatureRange dailyTemperatureRangeFromDb(
      Grid grid, Instant forecastedAt, Instant dayStart, Instant dayEnd
  ) {
    List<Weather> dayForecasts = weatherRepository
        .findByGridAndForecastedAtAndForecastAtGreaterThanEqualAndForecastAtLessThan(
            grid, forecastedAt, dayStart, dayEnd);
    return new TemperatureRange(
        dayForecasts.stream()
            .mapToDouble(w -> orElseZero(w.getTemperatureMin() != null ? w.getTemperatureMin() : w.getTemperatureCurrent()))
            .min().orElseThrow(() -> noDailyForecastsFound(dayStart)),
        dayForecasts.stream()
            .mapToDouble(w -> orElseZero(w.getTemperatureMax() != null ? w.getTemperatureMax() : w.getTemperatureCurrent()))
            .max().orElseThrow(() -> noDailyForecastsFound(dayStart))
    );
  }

  // grid+forecastedAt으로 찾은 배치 안에 그 날짜(dayStart 기준) 예보가 하나도 없는, 정상적으로는 있을 수 없는 상태
  private DailyForecastNotFoundException noDailyForecastsFound(Instant dayStart) {
    return new DailyForecastNotFoundException(dayStart);
  }

  // null이면 0.0으로 리턴 (dailyTemperatureRangeFromDb의 min/max 집계에서만 사용)
  private double orElseZero(Double value) {
    return value != null ? value : 0.0;
  }
}
