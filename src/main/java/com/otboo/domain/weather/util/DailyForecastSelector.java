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
import org.springframework.stereotype.Component;

@Component
public class DailyForecastSelector {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public List<WeatherDto> select(List<WeatherDto> forecasts, Instant now) {
    Map<LocalDate, List<WeatherDto>> byDate = forecasts.stream()
        .collect(Collectors.groupingBy(dto -> dto.forecastAt().atZone(KST).toLocalDate()));

    LocalDate today = now.atZone(KST).toLocalDate();
    WeatherDto representative = closest(byDate.get(today), now);
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