package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.PrecipitationType;
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
}
