package com.otboo.domain.feed.clothes.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeedClothesExceptionTest {

  @Test
  @DisplayName("피드 의상 없음 예외 생성")
  void feedClothesNotFoundException_success() {
    FeedClothesNotFoundException exception = new FeedClothesNotFoundException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exception.getMessage()).isNotBlank();
  }
}