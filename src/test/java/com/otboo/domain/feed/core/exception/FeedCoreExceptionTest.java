package com.otboo.domain.feed.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeedCoreExceptionTest {
  @Test
  @DisplayName("피드 권한 없음 예외 생성")
  void feedForbiddenException_success() {
    FeedForbiddenException exception = new FeedForbiddenException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("피드 없음 예외 생성")
  void feedNotFoundException_success() {
    UUID feedId = UUID.randomUUID();

    FeedNotFoundException exception = new FeedNotFoundException(feedId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(feedId.toString());
  }

  @Test
  @DisplayName("피드 사용자 없음 예외 생성")
  void feedUserNotFoundException_success() {
    UUID userId = UUID.randomUUID();

    FeedUserNotFoundException exception = new FeedUserNotFoundException(userId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(userId.toString());
  }

  @Test
  @DisplayName("피드 날씨 없음 예외 생성")
  void feedWeatherNotFoundException_success() {
    UUID weatherId = UUID.randomUUID();

    FeedWeatherNotFoundException exception = new FeedWeatherNotFoundException(weatherId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(weatherId.toString());
  }

  @Test
  @DisplayName("피드 커서 오류 예외 생성")
  void invalidFeedCursorException_success() {
    InvalidFeedCursorException exception = new InvalidFeedCursorException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }
}
