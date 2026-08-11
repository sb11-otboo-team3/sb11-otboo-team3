package com.otboo.domain.auth.jwt;

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

class JwtPropertiesTest {

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
  @DisplayName("adminAccessExpiration이 accessExpiration보다 짧으면 검증을 통과한다")
  void validWhenAdminExpirationIsShorter() {
    // given
    JwtProperties properties = new JwtProperties(
        "3WayDafV59YynmTwCpaDtnpeur8sokrAkQ+wFlXO4QY=",
        900000L,
        604800000L,
        300000L
    );

    // when
    Set<ConstraintViolation<JwtProperties>> violations = validator.validate(properties);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("adminAccessExpiration이 accessExpiration과 같으면 검증에 실패한다")
  void invalidWhenAdminExpirationEqualsDefault() {
    // given
    JwtProperties properties = new JwtProperties(
        "3WayDafV59YynmTwCpaDtnpeur8sokrAkQ+wFlXO4QY=",
        900000L,
        604800000L,
        900000L
    );

    // when
    Set<ConstraintViolation<JwtProperties>> violations = validator.validate(properties);

    // then
    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .extracting(ConstraintViolation::getMessage)
        .contains("adminAccessExpiration은 accessExpiration보다 짧아야 합니다.");
  }

  @Test
  @DisplayName("adminAccessExpiration이 accessExpiration보다 길면 검증에 실패한다")
  void invalidWhenAdminExpirationLongerThanDefault() {
    // given
    JwtProperties properties = new JwtProperties(
        "3WayDafV59YynmTwCpaDtnpeur8sokrAkQ+wFlXO4QY=",
        900000L,
        604800000L,
        1800000L
    );

    // when
    Set<ConstraintViolation<JwtProperties>> violations = validator.validate(properties);

    // then
    assertThat(violations).isNotEmpty();
  }
}