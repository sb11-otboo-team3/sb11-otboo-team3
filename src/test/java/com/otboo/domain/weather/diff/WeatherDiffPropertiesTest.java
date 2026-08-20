package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherDiffPropertiesTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  @DisplayName("모든 임계값이 유효 범위면 검증을 통과한다")
  void validWhenAllThresholdsInRange() {
    // given
    WeatherDiffProperties properties = new WeatherDiffProperties(5.0, 3.0);

    // when
    Set<ConstraintViolation<WeatherDiffProperties>> violations = validator.validate(properties);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("발표별 기온 임계값이 0 이하면 검증에 실패한다")
  void invalidWhenAnnouncementTempThresholdNotPositive() {
    // given
    WeatherDiffProperties properties = new WeatherDiffProperties(0.0, 3.0);

    // when
    Set<ConstraintViolation<WeatherDiffProperties>> violations = validator.validate(properties);

    // then
    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("일일별 기온 시간당 임계값이 0 이하면 검증에 실패한다")
  void invalidWhenDailyTempRateThresholdNotPositive() {
    // given
    WeatherDiffProperties properties = new WeatherDiffProperties(5.0, 0.0);

    // when
    Set<ConstraintViolation<WeatherDiffProperties>> violations = validator.validate(properties);

    // then
    assertThat(violations).isNotEmpty();
  }
}
