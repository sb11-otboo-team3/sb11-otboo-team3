package com.otboo.domain.notification.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NotificationExceptionTest {

  @Test
  @DisplayName("알림 권한 없음 예외 생성")
  void notificationForbiddenException_success() {
    NotificationForbiddenException exception = new NotificationForbiddenException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("알림 없음 예외 생성")
  void notificationNotFoundException_success() {
    UUID notificationId = UUID.randomUUID();

    NotificationNotFoundException exception =
        new NotificationNotFoundException(notificationId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(notificationId.toString());
  }

  @Test
  @DisplayName("알림 수신자 없음 예외 생성")
  void notificationUserNotFoundException_success() {
    UUID userId = UUID.randomUUID();

    NotificationUserNotFoundException exception =
        new NotificationUserNotFoundException(userId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(userId.toString());
  }

  @Test
  @DisplayName("알림 커서 오류 예외 생성")
  void invalidNotificationCursorException_success() {
    InvalidNotificationCursorException exception =
        new InvalidNotificationCursorException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }
}