package com.otboo.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MaxUtf8BytesValidatorTest {

  private MaxUtf8BytesValidator validator;

  @BeforeEach
  void setUp() {
    validator = new MaxUtf8BytesValidator();
    MaxUtf8Bytes annotation = Mockito.mock(MaxUtf8Bytes.class);
    Mockito.when(annotation.value()).thenReturn(72);
    validator.initialize(annotation);
  }

  @Test
  @DisplayName("null 값은 유효한 것으로 처리한다 (다른 어노테이션에 위임)")
  void nullIsValid() {
    assertThat(validator.isValid(null, mockContext())).isTrue();
  }

  @Test
  @DisplayName("영문 72바이트는 통과한다")
  void seventyTwoAsciiBytesIsValid() {
    String password = "a".repeat(72);
    assertThat(validator.isValid(password, mockContext())).isTrue();
  }

  @Test
  @DisplayName("영문 73바이트는 실패한다")
  void seventyThreeAsciiBytesIsInvalid() {
    String password = "a".repeat(73);
    assertThat(validator.isValid(password, mockContext())).isFalse();
  }

  @Test
  @DisplayName("한글 24자(72바이트)는 통과한다")
  void twentyFourKoreanCharsIsValid() {
    String password = "가".repeat(24);
    assertThat(validator.isValid(password, mockContext())).isTrue();
  }

  @Test
  @DisplayName("한글 25자(75바이트)는 실패한다")
  void twentyFiveKoreanCharsIsInvalid() {
    String password = "가".repeat(25);
    assertThat(validator.isValid(password, mockContext())).isFalse();
  }

  private ConstraintValidatorContext mockContext() {
    return Mockito.mock(ConstraintValidatorContext.class);
  }
}