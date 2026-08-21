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

  // 발표별은 "같은 시각(forecastAt)에 대한 예측값이 수정됐다"는 뜻이지 "그 시각까지 날씨가
  // 이렇게 변해간다"는 뜻이 아니다 - "오를 예정" 류의 시간-흐름 어투는 일일별과 헷갈리므로,
  // forecastAt 시각 + "조정/추가" 어투로 예보 자체가 바뀌었음을 명시한다.
  public String buildAnnouncementMessage(WeatherAnnouncementDiffEvent event) {
    List<String> clauses = new ArrayList<>();
    Weather previous = event.previous();
    Weather current = event.current();
    int hour = current.getForecastAt().atZone(KST).getHour();

    if (event.triggeredCategories().contains(DiffCategory.TEMPERATURE)) {
      clauses.add(temperatureAnnouncementClause(hour, previous.getTemperatureCurrent(), current.getTemperatureCurrent()));
    }
    if (event.triggeredCategories().contains(DiffCategory.PRECIPITATION)) {
      clauses.add(precipitationAnnouncementClause(
          hour, current.getPrecipitationType(), previous.getPrecipitationProbability(), current.getPrecipitationProbability()));
    }
    if (event.triggeredCategories().contains(DiffCategory.WIND)) {
      clauses.add(windAnnouncementClause(hour, previous.getWindSpeed(), current.getWindSpeed()));
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

  private String temperatureAnnouncementClause(int hour, double previousTemp, double currentTemp) {
    String direction = currentTemp > previousTemp ? "상향" : "하향";
    return "%d시 기온 예보가 %.1f°C에서 %.1f°C로 %s 조정됐어요".formatted(hour, previousTemp, currentTemp, direction);
  }

  private String precipitationAnnouncementClause(
      int hour, PrecipitationType currentType, double previousProbability, double currentProbability) {
    return "%d시 %s 예보가 새로 추가됐어요 (강수확률 %.0f%%→%.0f%%)".formatted(
        hour, precipitationWord(currentType), previousProbability, currentProbability);
  }

  private String windAnnouncementClause(int hour, double previousSpeed, double currentSpeed) {
    return "%d시 바람 예보가 더 강해지는 쪽으로 조정됐어요 (%.1fm/s→%.1fm/s)".formatted(hour, previousSpeed, currentSpeed);
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
