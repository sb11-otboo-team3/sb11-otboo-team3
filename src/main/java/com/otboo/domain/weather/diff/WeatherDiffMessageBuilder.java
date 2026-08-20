package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.Weather;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

// 급변 감지 이벤트를 사람이 읽는 알림 문구로 바꾼다. 여러 카테고리가 동시에 걸리면 문장을 이어붙여서
// 한 알림에 다 담는다 - 카테고리별로 알림을 따로따로 보내지 않기로 한 설계 결정을 여기서 구현.
@Component
public class WeatherDiffMessageBuilder {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public String buildAnnouncementMessage(WeatherAnnouncementDiffEvent event) {
    List<String> clauses = new ArrayList<>();
    Weather previous = event.previous();
    Weather current = event.current();

    if (event.triggeredCategories().contains(DiffCategory.TEMPERATURE)) {
      clauses.add(temperatureAnnouncementClause(previous.getTemperatureCurrent(), current.getTemperatureCurrent()));
    }
    if (event.triggeredCategories().contains(DiffCategory.PRECIPITATION)) {
      clauses.add(precipitationAnnouncementClause(
          current.getPrecipitationType(), previous.getPrecipitationProbability(), current.getPrecipitationProbability()));
    }
    if (event.triggeredCategories().contains(DiffCategory.WIND)) {
      clauses.add(windAnnouncementClause(previous.getWindSpeed(), current.getWindSpeed()));
    }

    return String.join(" ", clauses);
  }

  // 일일별은 구체적인 수치가 없다(DailyDiffTrigger엔 카테고리+시작 시각만 있음) - "언제부터"만 알려준다.
  public String buildDailyMessage(WeatherDailyDiffEvent event) {
    List<String> clauses = new ArrayList<>();
    for (DiffCategory category : DiffCategory.values()) {
      findTrigger(event.triggeredCategories(), category).ifPresent(trigger -> clauses.add(dailyClause(trigger)));
    }
    return String.join(" ", clauses);
  }

  private Optional<DailyDiffTrigger> findTrigger(Set<DailyDiffTrigger> triggers, DiffCategory category) {
    return triggers.stream().filter(trigger -> trigger.category() == category).findFirst();
  }

  private String temperatureAnnouncementClause(double previousTemp, double currentTemp) {
    String direction = currentTemp > previousTemp ? "오를" : "내릴";
    return "기온이 %.1f°C에서 %.1f°C로 %s 예정이에요".formatted(previousTemp, currentTemp, direction);
  }

  private String precipitationAnnouncementClause(
      PrecipitationType currentType, double previousProbability, double currentProbability) {
    return "%s 소식이 있어요 (강수확률 %.0f%%→%.0f%%)".formatted(
        precipitationWord(currentType), previousProbability, currentProbability);
  }

  private String windAnnouncementClause(double previousSpeed, double currentSpeed) {
    return "바람이 강해질 예정이에요 (%.1fm/s→%.1fm/s)".formatted(previousSpeed, currentSpeed);
  }

  private String dailyClause(DailyDiffTrigger trigger) {
    int hour = trigger.fromTime().atZone(KST).getHour();
    return switch (trigger.category()) {
      case TEMPERATURE -> "%d시부터 기온이 급격하게 %s 것 같아요".formatted(hour, trigger.rising() ? "오를" : "내릴");
      case PRECIPITATION -> "%d시부터 비/눈 소식이 있을 것 같아요".formatted(hour);
      case WIND -> "%d시부터 바람이 강해질 것 같아요".formatted(hour);
    };
  }

  private String precipitationWord(PrecipitationType type) {
    return switch (type) {
      case RAIN -> "비";
      case SNOW -> "눈";
      case RAIN_SNOW -> "비/눈";
      case SHOWER -> "소나기";
      case NONE -> "강수";
    };
  }
}
