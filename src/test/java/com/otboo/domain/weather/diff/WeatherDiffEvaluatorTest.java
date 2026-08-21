package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherDiffEvaluatorTest {

  private final WeatherDiffEvaluator evaluator = new WeatherDiffEvaluator();
  private final WeatherDiffProperties properties = new WeatherDiffProperties(5.0, 3.0);

  @Test
  @DisplayName("기온 변화가 임계값 미만이면 급변으로 판정하지 않는다")
  void notTriggeredWhenTemperatureDeltaBelowThreshold() {
    // when
    boolean triggered = evaluator.isTemperatureTriggered(22.0, 24.0, properties);

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  @DisplayName("기온이 임계값만큼 상승하면 급변으로 판정한다")
  void triggeredWhenTemperatureRisesAtThreshold() {
    // when
    boolean triggered = evaluator.isTemperatureTriggered(20.0, 25.0, properties);

    // then
    assertThat(triggered).isTrue();
  }

  @Test
  @DisplayName("기온이 임계값만큼 하락해도 급변으로 판정한다")
  void triggeredWhenTemperatureFallsAtThreshold() {
    // when
    boolean triggered = evaluator.isTemperatureTriggered(25.0, 20.0, properties);

    // then
    assertThat(triggered).isTrue();
  }

  @Test
  @DisplayName("강수형태가 NONE에서 강수로 전환되면 급변으로 판정한다")
  void triggeredWhenPrecipitationStartsFromNone() {
    // when
    boolean triggered = evaluator.isPrecipitationTriggered(PrecipitationType.NONE, PrecipitationType.RAIN);

    // then
    assertThat(triggered).isTrue();
  }

  @Test
  @DisplayName("강수형태가 강수에서 NONE으로 전환되면 급변으로 판정하지 않는다")
  void notTriggeredWhenPrecipitationStopsToNone() {
    // when
    boolean triggered = evaluator.isPrecipitationTriggered(PrecipitationType.RAIN, PrecipitationType.NONE);

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  @DisplayName("강수형태가 NONE으로 그대로면 급변으로 판정하지 않는다")
  void notTriggeredWhenPrecipitationTypeUnchangedAsNone() {
    // when
    boolean triggered = evaluator.isPrecipitationTriggered(PrecipitationType.NONE, PrecipitationType.NONE);

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  @DisplayName("강수형태가 강수로 그대로면 급변으로 판정하지 않는다")
  void notTriggeredWhenPrecipitationTypeUnchangedAsPrecipitating() {
    // when
    boolean triggered = evaluator.isPrecipitationTriggered(PrecipitationType.RAIN, PrecipitationType.RAIN);

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  @DisplayName("풍속 등급이 한 단계 오르면 급변으로 판정한다")
  void triggeredWhenWindStrengthTierRisesOneStep() {
    // when
    boolean triggered = evaluator.isWindTriggered(3.0, 5.0); // WEAK -> MODERATE

    // then
    assertThat(triggered).isTrue();
  }

  @Test
  @DisplayName("풍속 등급이 두 단계 올라도 급변으로 판정한다")
  void triggeredWhenWindStrengthTierRisesTwoSteps() {
    // when
    boolean triggered = evaluator.isWindTriggered(3.0, 10.0); // WEAK -> STRONG

    // then
    assertThat(triggered).isTrue();
  }

  @Test
  @DisplayName("풍속 등급이 내려가면 급변으로 판정하지 않는다")
  void notTriggeredWhenWindStrengthTierFalls() {
    // when
    boolean triggered = evaluator.isWindTriggered(10.0, 3.0); // STRONG -> WEAK

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  @DisplayName("풍속 등급이 그대로면 급변으로 판정하지 않는다")
  void notTriggeredWhenWindStrengthTierUnchanged() {
    // when
    boolean triggered = evaluator.isWindTriggered(2.0, 3.0); // 둘 다 WEAK

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  @DisplayName("forecastAt이 다음 발표 시각보다 이전이면 스코프 안에 든다")
  void withinWindowWhenForecastAtBeforeNextAnnouncement() {
    // given
    Instant forecastAt = Instant.parse("2026-07-30T05:00:00Z");
    Instant nextAnnouncementAt = Instant.parse("2026-07-30T08:00:00Z");

    // when
    boolean within = evaluator.isWithinNextAnnouncementWindow(forecastAt, nextAnnouncementAt);

    // then
    assertThat(within).isTrue();
  }

  @Test
  @DisplayName("forecastAt이 다음 발표 시각과 같거나 이후면 스코프 밖이다")
  void notWithinWindowWhenForecastAtAtOrAfterNextAnnouncement() {
    // given
    Instant forecastAt = Instant.parse("2026-07-30T08:00:00Z");
    Instant nextAnnouncementAt = Instant.parse("2026-07-30T08:00:00Z");

    // when
    boolean within = evaluator.isWithinNextAnnouncementWindow(forecastAt, nextAnnouncementAt);

    // then
    assertThat(within).isFalse();
  }

  // ===== 일일별: 기온 시간당 변화율 =====

  @Test
  @DisplayName("1시간 간격에서 시간당 변화율이 임계값 미만이면 급변으로 판정하지 않는다")
  void notTriggeredByRateWhenOneHourGapBelowThreshold() {
    // when: Δ2 / 1h = 2°C/h < 3°C/h
    boolean triggered = evaluator.isTemperatureTriggeredByRate(20.0, 22.0, 1.0, properties);

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  @DisplayName("1시간 간격에서 시간당 변화율이 임계값과 같으면 급변으로 판정한다")
  void triggeredByRateWhenOneHourGapAtThreshold() {
    // when: Δ3 / 1h = 3°C/h == 3°C/h
    boolean triggered = evaluator.isTemperatureTriggeredByRate(20.0, 23.0, 1.0, properties);

    // then
    assertThat(triggered).isTrue();
  }

  @Test
  @DisplayName("3시간 간격이라도 시간당 변화율이 임계값과 같으면 급변으로 판정한다")
  void triggeredByRateWhenThreeHourGapAtThreshold() {
    // when: Δ9 / 3h = 3°C/h == 3°C/h - 같은 절대값(9도)이라도 간격이 넓으면 다르게 판정돼야 함을 증명
    boolean triggered = evaluator.isTemperatureTriggeredByRate(15.0, 24.0, 3.0, properties);

    // then
    assertThat(triggered).isTrue();
  }

  @Test
  @DisplayName("3시간 간격에서는 절대값이 커도 시간당 변화율이 임계값 미만이면 급변으로 판정하지 않는다")
  void notTriggeredByRateWhenThreeHourGapBelowThreshold() {
    // when: Δ8 / 3h ≈ 2.67°C/h < 3°C/h - 1시간 기준으론 컸을 Δ지만 3시간에 걸쳐 일어난 거라 급변 아님
    boolean triggered = evaluator.isTemperatureTriggeredByRate(15.0, 23.0, 3.0, properties);

    // then
    assertThat(triggered).isFalse();
  }

  // ===== 일일별: 하루치 인접 슬롯 스캔 =====

  // temperature/windSpeed를 Double(boxed)로 받아 null(결측치) 케이스도 만들 수 있게 한다.
  private Weather slot(Instant forecastAt, Double temperature, PrecipitationType precipitationType, Double windSpeed) {
    return Weather.builder()
        .grid(Grid.builder().x(60).y(127).build())
        .forecastedAt(forecastAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(precipitationType)
        .temperatureCurrent(temperature)
        .windSpeed(windSpeed)
        .build();
  }

  @Test
  @DisplayName("행이 하나 이하면 비교할 인접 쌍이 없어 빈 집합을 반환한다")
  void evaluateDailyDiffReturnsEmptyWhenFewerThanTwoRows() {
    // given
    List<Weather> single = List.of(slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0));

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(single, properties);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("인접 슬롯 간 기온이 오르며 변화율이 임계값을 넘으면 변경이 확정된(뒤쪽) 시각과 함께 TEMPERATURE(rising)를 반환한다")
  void evaluateDailyDiffDetectsRisingTemperature() {
    // given: 1시간 간격, Δ4 -> 4°C/h ≥ 3°C/h
    Instant triggerTime = Instant.parse("2026-07-30T01:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(triggerTime, 24.0, PrecipitationType.NONE, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.TEMPERATURE, triggerTime, true));
  }

  @Test
  @DisplayName("인접 슬롯 간 기온이 내리며 변화율이 임계값을 넘으면 rising=false로 반환한다")
  void evaluateDailyDiffDetectsFallingTemperature() {
    // given
    Instant triggerTime = Instant.parse("2026-07-30T01:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 24.0, PrecipitationType.NONE, 2.0),
        slot(triggerTime, 20.0, PrecipitationType.NONE, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.TEMPERATURE, triggerTime, false));
  }

  @Test
  @DisplayName("인접 슬롯 간 강수형태가 NONE에서 강수로 바뀌면 변경이 확정된(뒤쪽) 시각과 함께 PRECIPITATION을 반환한다")
  void evaluateDailyDiffDetectsPrecipitation() {
    // given: previous 시각(NONE)을 쓰면 그 시각 실제 데이터와 모순되므로, 강수가 확정된 current 시각을 기대한다
    Instant triggerTime = Instant.parse("2026-07-30T01:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(triggerTime, 20.0, PrecipitationType.RAIN, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.PRECIPITATION, triggerTime, true));
  }

  @Test
  @DisplayName("인접 슬롯 간 풍속 등급이 오르면 변경이 확정된(뒤쪽) 시각과 함께 WIND를 반환한다")
  void evaluateDailyDiffDetectsWind() {
    // given
    Instant triggerTime = Instant.parse("2026-07-30T01:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(triggerTime, 20.0, PrecipitationType.NONE, 10.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.WIND, triggerTime, true));
  }

  @Test
  @DisplayName("첫 인접 쌍은 안 걸려도 다음 인접 쌍이 걸리면 그 쌍의 변경 확정 시각으로 반환한다")
  void evaluateDailyDiffDetectsTriggerInAnySubsequentPair() {
    // given: 00시->01시는 변화 없음, 01시->02시에 기온만 크게 뜀 - 02시가 변경이 확정된 시각
    Instant secondPairTriggerTime = Instant.parse("2026-07-30T02:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(Instant.parse("2026-07-30T01:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(secondPairTriggerTime, 25.0, PrecipitationType.NONE, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.TEMPERATURE, secondPairTriggerTime, true));
  }

  @Test
  @DisplayName("같은 카테고리가 여러 쌍에서 걸려도 가장 이른 쌍의 변경 확정 시각만 반환한다")
  void evaluateDailyDiffReportsEarliestOccurrenceWhenTriggeredMultipleTimes() {
    // given: 00시->01시, 01시->02시 둘 다 기온 급변 - 더 이른 쌍(00시->01시)의 확정 시각인 01시가 반환돼야 함
    Instant earliestTriggerTime = Instant.parse("2026-07-30T01:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(earliestTriggerTime, 24.0, PrecipitationType.NONE, 2.0),
        slot(Instant.parse("2026-07-30T02:00:00Z"), 28.0, PrecipitationType.NONE, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.TEMPERATURE, earliestTriggerTime, true));
  }

  @Test
  @DisplayName("어느 인접 쌍도 걸리지 않으면 빈 집합을 반환한다")
  void evaluateDailyDiffReturnsEmptyWhenNoPairTriggers() {
    // given
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(Instant.parse("2026-07-30T01:00:00Z"), 21.0, PrecipitationType.NONE, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("강수 카테고리가 여러 쌍에서 걸려도 가장 이른 쌍의 변경 확정 시각만 반환한다")
  void evaluateDailyDiffReportsEarliestPrecipitationOccurrenceWhenTriggeredMultipleTimes() {
    // given: 00시->01시에 비가 시작되고(NONE->RAIN), 02시->03시에 그쳤다가 다시 시작돼도(NONE->RAIN)
    // 하루에 한 번만 알리므로 더 이른 쌍의 확정 시각인 01시만 반환돼야 함
    Instant earliestTriggerTime = Instant.parse("2026-07-30T01:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(earliestTriggerTime, 20.0, PrecipitationType.RAIN, 2.0),
        slot(Instant.parse("2026-07-30T02:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(Instant.parse("2026-07-30T03:00:00Z"), 20.0, PrecipitationType.RAIN, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.PRECIPITATION, earliestTriggerTime, true));
  }

  @Test
  @DisplayName("풍속 카테고리가 여러 쌍에서 걸려도 가장 이른 쌍의 변경 확정 시각만 반환한다")
  void evaluateDailyDiffReportsEarliestWindOccurrenceWhenTriggeredMultipleTimes() {
    // given: 00시->01시에 WEAK->STRONG, 01시->02시에 다시 WEAK로 내려갔다가, 02시->03시에 또 STRONG으로
    // 올라도 하루에 한 번만 알리므로 더 이른 쌍의 확정 시각인 01시만 반환돼야 함
    Instant earliestTriggerTime = Instant.parse("2026-07-30T01:00:00Z");
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(earliestTriggerTime, 20.0, PrecipitationType.NONE, 10.0),
        slot(Instant.parse("2026-07-30T02:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(Instant.parse("2026-07-30T03:00:00Z"), 20.0, PrecipitationType.NONE, 10.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).containsExactly(new DailyDiffTrigger(DiffCategory.WIND, earliestTriggerTime, true));
  }

  @Test
  @DisplayName("쌍의 앞쪽 슬롯 기온이 null이면 그 쌍은 기온 판정을 건너뛴다")
  void evaluateDailyDiffSkipsTemperatureWhenPreviousValueIsNull() {
    // given: 기상청 응답 결측 등으로 temperatureCurrent가 없는 슬롯 - 예외 없이 건너뛰어야 함
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), null, PrecipitationType.NONE, 2.0),
        slot(Instant.parse("2026-07-30T01:00:00Z"), 24.0, PrecipitationType.NONE, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("쌍의 뒤쪽 슬롯 기온이 null이면 그 쌍은 기온 판정을 건너뛴다")
  void evaluateDailyDiffSkipsTemperatureWhenCurrentValueIsNull() {
    // given
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 2.0),
        slot(Instant.parse("2026-07-30T01:00:00Z"), null, PrecipitationType.NONE, 2.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("쌍의 앞쪽 슬롯 풍속이 null이면 그 쌍은 풍속 판정을 건너뛴다")
  void evaluateDailyDiffSkipsWindWhenPreviousValueIsNull() {
    // given
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, null),
        slot(Instant.parse("2026-07-30T01:00:00Z"), 20.0, PrecipitationType.NONE, 10.0)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("쌍의 뒤쪽 슬롯 풍속이 null이면 그 쌍은 풍속 판정을 건너뛴다")
  void evaluateDailyDiffSkipsWindWhenCurrentValueIsNull() {
    // given
    List<Weather> rows = List.of(
        slot(Instant.parse("2026-07-30T00:00:00Z"), 20.0, PrecipitationType.NONE, 10.0),
        slot(Instant.parse("2026-07-30T01:00:00Z"), 20.0, PrecipitationType.NONE, null)
    );

    // when
    Set<DailyDiffTrigger> result = evaluator.evaluateDailyDiff(rows, properties);

    // then
    assertThat(result).isEmpty();
  }
}
