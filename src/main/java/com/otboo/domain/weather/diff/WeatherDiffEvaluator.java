package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import java.time.Duration;
import java.time.Instant;
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
  // 결측치(null) 체크를 여기서 하니 호출부(WeatherPersister, evaluateDailyDiff)마다 반복하지 않아도 된다.
  public boolean isTemperatureTriggered(Double previousTemp, Double currentTemp, WeatherDiffProperties properties) {
    if (previousTemp == null || currentTemp == null) {
      return false;
    }
    return Math.abs(currentTemp - previousTemp) >= properties.announcementTempThreshold();
  }

  // 강수는 악화 방향(NONE→강수)만 판정한다 - 그치는 건 몰라도 무해하지만 시작되는 건 놓치면 곤란하므로.
  public boolean isPrecipitationTriggered(PrecipitationType previousType, PrecipitationType currentType) {
    return previousType == PrecipitationType.NONE && currentType != PrecipitationType.NONE;
  }

  // 풍속도 악화 방향(등급 상승)만 판정한다 - WindStrength 선언 순서(WEAK<MODERATE<STRONG)가
  // 그대로 심각도 순서라 ordinal 비교로 등급이 올라갔는지 알 수 있다.
  public boolean isWindTriggered(Double previousSpeed, Double currentSpeed) {
    if (previousSpeed == null || currentSpeed == null) {
      return false;
    }
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
      Double previousTemp, Double currentTemp, double gapHours, WeatherDiffProperties properties) {
    if (previousTemp == null || currentTemp == null) {
      return false;
    }
    return Math.abs(currentTemp - previousTemp) / gapHours >= properties.dailyTempRateThresholdPerHour();
  }

  // 하루치(같은 forecastedAt, forecastAt으로 정렬된) row를 인접 쌍끼리 훑어서, 카테고리별로 "가장 이른"
  // 트리거 쌍 하나만 담는다(사용자에게는 "언제부터"가 제일 중요하므로) - 강수·풍속은 발표별과 같은 규칙을
  // 그대로 재사용한다(카테고리 전환이면 간격 크기와 무관하게 이미 의미 있는 변화라 시간으로 나눌 필요 없음).
  public Set<DailyDiffTrigger> evaluateDailyDiff(List<Weather> sortedByForecastAt, WeatherDiffProperties properties) {
    Set<DailyDiffTrigger> triggers = new LinkedHashSet<>();

    for (int i = 1; i < sortedByForecastAt.size(); i++) {
      Weather previous = sortedByForecastAt.get(i - 1);
      Weather current = sortedByForecastAt.get(i);
      // previous 시각을 쓰면, 그 시각의 실제 데이터(아직 안 바뀐 값)와 모순되는 문구가 나간다
      // (예: 강수는 previous가 아직 NONE인 시각인데 "그때부터 비 소식"이라 하면 틀린 말이 됨) -
      // 새 값이 확정되는 current 시각을 기준으로 알린다.
      Instant fromTime = current.getForecastAt();

      double gapHours = Duration.between(previous.getForecastAt(), current.getForecastAt()).toMinutes() / 60.0;

      if (!alreadyTriggered(triggers, DiffCategory.TEMPERATURE)
          && isTemperatureTriggeredByRate(
              previous.getTemperatureCurrent(), current.getTemperatureCurrent(), gapHours, properties)) {
        boolean rising = current.getTemperatureCurrent() > previous.getTemperatureCurrent();
        triggers.add(new DailyDiffTrigger(DiffCategory.TEMPERATURE, fromTime, rising));
      }
      if (!alreadyTriggered(triggers, DiffCategory.PRECIPITATION)
          && isPrecipitationTriggered(previous.getPrecipitationType(), current.getPrecipitationType())) {
        triggers.add(new DailyDiffTrigger(DiffCategory.PRECIPITATION, fromTime, true));
      }
      if (!alreadyTriggered(triggers, DiffCategory.WIND)
          && isWindTriggered(previous.getWindSpeed(), current.getWindSpeed())) {
        triggers.add(new DailyDiffTrigger(DiffCategory.WIND, fromTime, true));
      }
    }

    return triggers;
  }

  // "이 카테고리로 이미 하루치 트리거를 담았는지"를 triggers 자체에서 되묻는다 - 별도 Set을 나란히
  // 유지하며 두 컬렉션의 add를 매번 짝 맞춰야 하는 부담을 없앤다.
  private boolean alreadyTriggered(Set<DailyDiffTrigger> triggers, DiffCategory category) {
    return triggers.stream().anyMatch(trigger -> trigger.category() == category);
  }
}
