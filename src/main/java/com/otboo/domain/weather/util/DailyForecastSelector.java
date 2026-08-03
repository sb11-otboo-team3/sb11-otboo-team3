package com.otboo.domain.weather.util;

import com.otboo.domain.weather.dto.WeatherDto;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyForecastSelector {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public List<WeatherDto> select(List<WeatherDto> forecasts, Instant now) {
    Map<LocalDate, List<WeatherDto>> byDate = forecasts.stream()
        .collect(Collectors.groupingBy(dto -> dto.forecastAt().atZone(KST).toLocalDate()));

    LocalDate today = now.atZone(KST).toLocalDate();
    List<WeatherDto> todayForecasts = byDate.get(today);
    if (todayForecasts == null) {
      log.warn("일별 대표 예보 선정 - 오늘({}) 예보 없음, 전체 예보 중 현재 시각과 가장 가까운 항목으로 대체", today);
      todayForecasts = forecasts;
    }
    WeatherDto representative = closest(todayForecasts, now);
    LocalTime representativeTime = representative.forecastAt().atZone(KST).toLocalTime();

    return byDate.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> closest(entry.getValue(), entry.getKey().atTime(representativeTime).atZone(KST).toInstant()))
        .toList();
  }

  private WeatherDto closest(List<WeatherDto> candidates, Instant target) {
    return candidates.stream()
        .min(Comparator.comparing(dto -> Duration.between(target, dto.forecastAt()).abs()))
        .orElseThrow();
  }
}