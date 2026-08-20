package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;

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
}
