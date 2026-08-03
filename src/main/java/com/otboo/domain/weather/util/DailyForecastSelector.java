package com.otboo.domain.weather.util;

import com.otboo.domain.weather.dto.TemperatureDto;
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

  //서비스는 한국 시간 기준(기상청 API 사용하니까)
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");


  public List<WeatherDto> select(List<WeatherDto> forecasts, Instant now) {

    // 예보 대상 시각별로 매핑
    Map<LocalDate, List<WeatherDto>> byDate = forecasts.stream()
        .collect(Collectors.groupingBy(dto -> dto.forecastAt().atZone(KST).toLocalDate()));

    // 오늘 날짜
    LocalDate today = now.atZone(KST).toLocalDate();

    //오늘 날짜에 대한 예보 리스트 가져오기
    List<WeatherDto> todayForecasts = byDate.get(today);
    if (todayForecasts == null) {
      log.warn("일별 대표 예보 선정 - 오늘({}) 예보 없음, 전체 예보 중 현재 시각과 가장 가까운 항목으로 대체", today);
      todayForecasts = forecasts;
    }

    //현재 시각과 가장 가까운걸 대표로 고름.
    WeatherDto representative = closest(todayForecasts, now);
    // 거기서 그 시간만 고름.
    LocalTime representativeTime = representative.forecastAt().atZone(KST).toLocalTime();

    // 날짜별로 리스트 만들어 리턴.
    return byDate.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> withDailyMinMax(
            closest(entry.getValue(), entry.getKey().atTime(representativeTime).atZone(KST).toInstant()),
            entry.getValue()))
        .toList();
  }
  // 가장 근처 시간대 체크
  private WeatherDto closest(List<WeatherDto> candidates, Instant target) {
    return candidates.stream()
        .min(Comparator.comparing(dto -> Duration.between(target, dto.forecastAt()).abs()))
        .orElseThrow();
  }

  // 대표 시간대 항목엔 일 최저/최고(TMN/TMX)가 안 실려있는 경우가 많아, 대표값 자체가 아니라
  // 그날 전체 항목의 min/max를 모아서 채운다.
  private WeatherDto withDailyMinMax(WeatherDto representative, List<WeatherDto> dayForecasts) {
    double min = dayForecasts.stream().mapToDouble(dto -> dto.temperature().min()).min().orElseThrow();
    double max = dayForecasts.stream().mapToDouble(dto -> dto.temperature().max()).max().orElseThrow();

    TemperatureDto temperature = new TemperatureDto(
        representative.temperature().current(),
        representative.temperature().comparedToDayBefore(),
        min,
        max
    );

    return new WeatherDto(
        representative.id(),
        representative.forecastedAt(),
        representative.forecastAt(),
        representative.location(),
        representative.skyStatus(),
        representative.precipitation(),
        representative.humidity(),
        temperature,
        representative.windSpeed()
    );
  }
}