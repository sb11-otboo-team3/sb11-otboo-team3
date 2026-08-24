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
    assertThat(message).isEqualTo("09시 기온 예보가 20.0°C에서 26.0°C로 상향 조정됐어요.");
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
    assertThat(message).isEqualTo("09시 기온 예보가 26.0°C에서 20.0°C로 하향 조정됐어요.");
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
    assertThat(message).isEqualTo("09시 비 예보가 새로 추가됐어요 (강수확률 20%→75%).");
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
    assertThat(message).isEqualTo("09시 바람 예보가 더 강해지는 쪽으로 조정됐어요 (2.0m/s→10.0m/s).");
  }

  @Test
  @DisplayName("발표별 - 강수형태가 눈이면 눈으로 표시한다")
  void buildsAnnouncementMessageForSnow() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 20.0, 2.0);
    Weather current = weather(20.0, PrecipitationType.SNOW, 75.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("09시 눈 예보가 새로 추가됐어요 (강수확률 20%→75%).");
  }

  @Test
  @DisplayName("발표별 - 강수형태가 비/눈이면 비/눈으로 표시한다")
  void buildsAnnouncementMessageForRainSnow() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 20.0, 2.0);
    Weather current = weather(20.0, PrecipitationType.RAIN_SNOW, 75.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("09시 비/눈 예보가 새로 추가됐어요 (강수확률 20%→75%).");
  }

  @Test
  @DisplayName("발표별 - 강수형태가 소나기면 소나기로 표시한다")
  void buildsAnnouncementMessageForShower() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 20.0, 2.0);
    Weather current = weather(20.0, PrecipitationType.SHOWER, 75.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("09시 소나기 예보가 새로 추가됐어요 (강수확률 20%→75%).");
  }

  @Test
  @DisplayName("발표별 - 강수형태가 NONE이면 강수로 표시한다")
  void buildsAnnouncementMessageForNonePrecipitationType() {
    // given: 실제 트리거 경로(WeatherDiffEvaluator.isPrecipitationTriggered)는 NONE->강수 전환일 때만
    // PRECIPITATION 카테고리를 세우므로 이 경우가 실전에서 나오진 않지만, precipitationWord()가
    // 모든 enum 값에 대해 예외 없이 방어적으로 동작하는지 확인한다.
    Weather previous = weather(20.0, PrecipitationType.NONE, 20.0, 2.0);
    Weather current = weather(20.0, PrecipitationType.NONE, 75.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("09시 강수 예보가 새로 추가됐어요 (강수확률 20%→75%).");
  }

  private Weather weatherWithNullProbability(double temperature, PrecipitationType precipitationType, double windSpeed) {
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    return Weather.builder()
        .grid(grid)
        .forecastedAt(now)
        .forecastAt(now)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(precipitationType)
        .precipitationProbability(null)
        .temperatureCurrent(temperature)
        .windSpeed(windSpeed)
        .build();
  }

  @Test
  @DisplayName("발표별 - 이전 강수확률이 결측치(null)면 괄호 없이 문구를 만든다")
  void buildsAnnouncementMessageWithoutProbabilityWhenPreviousIsNull() {
    // given: KMA POP 파싱 실패 등으로 확률이 null이어도 예외 없이 문구는 만들어져야 한다
    Weather previous = weatherWithNullProbability(20.0, PrecipitationType.NONE, 2.0);
    Weather current = weather(20.0, PrecipitationType.RAIN, 75.0, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("09시 비 예보가 새로 추가됐어요.");
  }

  @Test
  @DisplayName("발표별 - 현재 강수확률이 결측치(null)면 괄호 없이 문구를 만든다")
  void buildsAnnouncementMessageWithoutProbabilityWhenCurrentIsNull() {
    // given
    Weather previous = weather(20.0, PrecipitationType.NONE, 20.0, 2.0);
    Weather current = weatherWithNullProbability(20.0, PrecipitationType.RAIN, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.PRECIPITATION));

    // when
    String message = messageBuilder.buildAnnouncementMessage(event);

    // then
    assertThat(message).isEqualTo("09시 비 예보가 새로 추가됐어요.");
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
    assertThat(message).isEqualTo(
        "09시 기온 예보가 20.0°C에서 26.0°C로 상향 조정됐어요.\n09시 비 예보가 새로 추가됐어요 (강수확률 20%→75%).");
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
    assertThat(message).isEqualTo("15시부터 기온이 급격하게 오를 것 같아요.");
  }

  @Test
  @DisplayName("일일별 - 기온 하강이면 시각과 함께 내리는 문구를 만든다")
  void buildsDailyMessageForFallingTemperature() {
    // given
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(
        grid, LocalDate.of(2026, 7, 30), Set.of(trigger(DiffCategory.TEMPERATURE, false)));

    // when
    String message = messageBuilder.buildDailyMessage(event);

    // then
    assertThat(message).isEqualTo("15시부터 기온이 급격하게 내릴 것 같아요.");
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
    assertThat(message).isEqualTo("15시부터 비/눈 소식이 있을 것 같아요.");
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
    assertThat(message).isEqualTo("15시부터 바람이 강해질 것 같아요.");
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
    assertThat(message).isEqualTo("15시부터 기온이 급격하게 오를 것 같아요.\n15시부터 바람이 강해질 것 같아요.");
  }

  @Test
  @DisplayName("일일별 - 여러 트리거가 있으면 카테고리 순서가 아니라 시각 순서로 이어붙인다")
  void buildsDailyMessageOrderedByTimeNotCategory() {
    // given: 카테고리 선언 순서(기온->강수->풍속)와 반대로, 풍속이 기온보다 이른 시각에 걸림
    DailyDiffTrigger earlierWind =
        new DailyDiffTrigger(DiffCategory.WIND, Instant.parse("2026-07-30T00:00:00Z"), true); // KST 09시
    DailyDiffTrigger laterTemperature =
        new DailyDiffTrigger(DiffCategory.TEMPERATURE, Instant.parse("2026-07-30T06:00:00Z"), true); // KST 15시
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(
        grid, LocalDate.of(2026, 7, 30), Set.of(laterTemperature, earlierWind));

    // when
    String message = messageBuilder.buildDailyMessage(event);

    // then: 카테고리 고정 순서대로면 기온이 먼저 나오겠지만, 시각 순서로는 풍속(09시)이 먼저다
    assertThat(message).isEqualTo("09시부터 바람이 강해질 것 같아요.\n15시부터 기온이 급격하게 오를 것 같아요.");
  }
}
