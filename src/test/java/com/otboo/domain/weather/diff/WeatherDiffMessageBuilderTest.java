package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherDiffMessageBuilderTest {

  private final WeatherDiffMessageBuilder messageBuilder = new WeatherDiffMessageBuilder();
  private final Grid grid = Grid.builder().x(60).y(127).build();

  private Weather weather(double temperature, PrecipitationType precipitationType,
      double precipitationProbability, double windSpeed) {
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    return Weather.builder()
        .grid(grid)
        .forecastedAt(now)
        .forecastAt(now)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(precipitationType)
        .precipitationProbability(precipitationProbability)
        .temperatureCurrent(temperature)
        .windSpeed(windSpeed)
        .build();
  }

  // ===== 발표별 =====

  @Test
  @DisplayName("발표별 - 기온이 오르면 오르는 문구를 만든다")
  void buildsAnnouncementMessageForRisingTemperature() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 10.0, 2.0);
    Weather current = weather(26.0, PrecipitationType.NONE, 10.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.TEMPERATURE));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("기온이 20.0°C에서 26.0°C로 오를 예정이에요");
  }

  @Test
  @DisplayName("발표별 - 기온이 내리면 내리는 문구를 만든다")
  void buildsAnnouncementMessageForFallingTemperature() {
    // given
    Weather previous = weather(26.0, PrecipitationType.NONE, 10.0, 2.0);
    Weather current = weather(20.0, PrecipitationType.NONE, 10.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.TEMPERATURE));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("기온이 26.0°C에서 20.0°C로 내릴 예정이에요");
  }

  @Test
  @DisplayName("발표별 - 강수형태 전환이면 강수확률을 곁들인 문구를 만든다")
  void buildsAnnouncementMessageForPrecipitation() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 20.0, 2.0);
    Weather current = weather(20.0, PrecipitationType.RAIN, 75.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("비 소식이 있어요 (강수확률 20%→75%)");
  }

  @Test
  @DisplayName("발표별 - 풍속 등급이 오르면 속도를 곁들인 문구를 만든다")
  void buildsAnnouncementMessageForWind() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 10.0, 2.0);
    Weather current = weather(20.0, PrecipitationType.NONE, 10.0, 10.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.WIND));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("바람이 강해질 예정이에요 (2.0m/s→10.0m/s)");
  }

  @Test
  @DisplayName("발표별 - 여러 카테고리가 걸리면 문구를 이어붙인다")
  void buildsAnnouncementMessageForMultipleCategories() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 20.0, 2.0);
    Weather current = weather(26.0, PrecipitationType.RAIN, 75.0, 2.0);
    WeatherAnnouncementDiffEvent event = new WeatherAnnouncementDiffEvent(
        previous, current, EnumSet.of(DiffCategory.TEMPERATURE, DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("기온이 20.0°C에서 26.0°C로 오를 예정이에요 비 소식이 있어요 (강수확률 20%→75%)");
  }

  // ===== 일일별 =====

  private DailyDiffTrigger trigger(DiffCategory category, boolean rising) {
    // 06:00Z = KST 15:00
    return new DailyDiffTrigger(category, Instant.parse("2026-07-30T06:00:00Z"), rising);
  }

  @Test
  @DisplayName("일일별 - 기온 상승이면 시각과 함께 오르는 문구를 만든다")
  void buildsDailyMessageForRisingTemperature() {
    // given
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(
        grid, LocalDate.of(2026, 7, 30), Set.of(trigger(DiffCategory.TEMPERATURE, true)));

    // when
    String message = messageBuilder.buildDailyMessage(event);

    // then
    assertThat(message).isEqualTo("15시부터 기온이 급격하게 오를 것 같아요");
  }

  @Test
  @DisplayName("일일별 - 강수 트리거면 시각과 함께 강수 문구를 만든다")
  void buildsDailyMessageForPrecipitation() {
    // given
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(
        grid, LocalDate.of(2026, 7, 30), Set.of(trigger(DiffCategory.PRECIPITATION, true)));

    // when
    String message = messageBuilder.buildDailyMessage(event);

    // then
    assertThat(message).isEqualTo("15시부터 비/눈 소식이 있을 것 같아요");
  }

  @Test
  @DisplayName("일일별 - 풍속 트리거면 시각과 함께 풍속 문구를 만든다")
  void buildsDailyMessageForWind() {
    // given
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(
        grid, LocalDate.of(2026, 7, 30), Set.of(trigger(DiffCategory.WIND, true)));

    // when
    String message = messageBuilder.buildDailyMessage(event);

    // then
    assertThat(message).isEqualTo("15시부터 바람이 강해질 것 같아요");
  }

  @Test
  @DisplayName("일일별 - 여러 트리거가 있으면 문구를 이어붙인다")
  void buildsDailyMessageForMultipleTriggers() {
    // given
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(
        grid, LocalDate.of(2026, 7, 30),
        Set.of(trigger(DiffCategory.TEMPERATURE, true), trigger(DiffCategory.WIND, true)));

    // when
    String message = messageBuilder.buildDailyMessage(event);

    // then
    assertThat(message).isEqualTo("15시부터 기온이 급격하게 오를 것 같아요 15시부터 바람이 강해질 것 같아요");
  }
}
