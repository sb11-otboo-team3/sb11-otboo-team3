package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

// 급변 카테고리별 순수 판정 로직. DB/Spring 컨텍스트 없이 이전/현재 값만 비교한다 -
// 발표별(WeatherPersister)·일일별(WeatherDailyDiffScheduler) 양쪽에서 재사용할 수 있게
// 배선 로직과 분리해뒀다.
@Component
public class WeatherDiffEvaluator {

  // 기온은 대칭(|Δ|) - 오르든 내리든 옷차림에 똑같이 영향을 주므로 방향 구분 없이 판정한다.
  public boolean isTemperatureTriggered(double previousTemp, double currentTemp, WeatherDiffProperties properties) {
    return Math.abs(currentTemp - previousTemp) >= properties.announcementTempThreshold();
  }

  // 강수는 악화 방향(NONE→강수)만 판정한다 - 그치는 건 몰라도 무해하지만 시작되는 건 놓치면 곤란하므로.
  public boolean isPrecipitationTriggered(PrecipitationType previousType, PrecipitationType currentType) {
    return previousType == PrecipitationType.NONE && currentType != PrecipitationType.NONE;
  }

  // 풍속도 악화 방향(등급 상승)만 판정한다 - WindStrength 선언 순서(WEAK<MODERATE<STRONG)가
  // 그대로 심각도 순서라 ordinal 비교로 등급이 올라갔는지 알 수 있다.
  public boolean isWindTriggered(double previousSpeed, double currentSpeed) {
    WindStrength previous = WindStrength.fromSpeed(previousSpeed);
    WindStrength current = WindStrength.fromSpeed(currentSpeed);
    return current.ordinal() > previous.ordinal();
  }

  // 발표별 비교 대상은 "다음 발표 전에 일어나는 시간대"로 제한한다 - 그보다 먼 미래는 다음 배치가
  // 다시 검증할 기회가 있으므로 지금 당장 볼 필요가 없다(WeatherPersister에서 사용).
  public boolean isWithinNextAnnouncementWindow(Instant forecastAt, Instant nextAnnouncementAt) {
    return forecastAt.isBefore(nextAnnouncementAt);
  }

  // 일일별 기온은 절대값이 아니라 시간당 변화율로 본다 - 인접 슬롯 간격이 1시간/3시간으로 섞여있어서,
  // 같은 임계값을 그대로 쓰면 짧은 간격에선 절대 안 걸리고 긴 간격에선 늘 걸리게 된다.
  public boolean isTemperatureTriggeredByRate(
      double previousTemp, double currentTemp, double gapHours, WeatherDiffProperties properties) {
    return Math.abs(currentTemp - previousTemp) / gapHours >= properties.dailyTempRateThresholdPerHour();
  }

  // 하루치(같은 forecastedAt, forecastAt으로 정렬된) row를 인접 쌍끼리 훑어서, 카테고리별로 "가장 이른"
  // 트리거 쌍 하나만 담는다(사용자에게는 "언제부터"가 제일 중요하므로) - 강수·풍속은 발표별과 같은 규칙을
  // 그대로 재사용한다(카테고리 전환이면 간격 크기와 무관하게 이미 의미 있는 변화라 시간으로 나눌 필요 없음).
  public Set<DailyDiffTrigger> evaluateDailyDiff(List<Weather> sortedByForecastAt, WeatherDiffProperties properties) {
    Set<DailyDiffTrigger> triggers = new LinkedHashSet<>();
    Set<DiffCategory> reportedCategories = EnumSet.noneOf(DiffCategory.class);

    for (int i = 1; i < sortedByForecastAt.size(); i++) {
      Weather previous = sortedByForecastAt.get(i - 1);
      Weather current = sortedByForecastAt.get(i);
      Instant fromTime = previous.getForecastAt();

      double gapHours = Duration.between(previous.getForecastAt(), current.getForecastAt()).toMinutes() / 60.0;

      if (!reportedCategories.contains(DiffCategory.TEMPERATURE)
          && previous.getTemperatureCurrent() != null && current.getTemperatureCurrent() != null
          && isTemperatureTriggeredByRate(
              previous.getTemperatureCurrent(), current.getTemperatureCurrent(), gapHours, properties)) {
        boolean rising = current.getTemperatureCurrent() > previous.getTemperatureCurrent();
        triggers.add(new DailyDiffTrigger(DiffCategory.TEMPERATURE, fromTime, rising));
        reportedCategories.add(DiffCategory.TEMPERATURE);
      }
      if (!reportedCategories.contains(DiffCategory.PRECIPITATION)
          && isPrecipitationTriggered(previous.getPrecipitationType(), current.getPrecipitationType())) {
        triggers.add(new DailyDiffTrigger(DiffCategory.PRECIPITATION, fromTime, true));
        reportedCategories.add(DiffCategory.PRECIPITATION);
      }
      if (!reportedCategories.contains(DiffCategory.WIND)
          && previous.getWindSpeed() != null && current.getWindSpeed() != null
          && isWindTriggered(previous.getWindSpeed(), current.getWindSpeed())) {
        triggers.add(new DailyDiffTrigger(DiffCategory.WIND, fromTime, true));
        reportedCategories.add(DiffCategory.WIND);
      }
    }

    return triggers;
  }
}
