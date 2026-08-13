package com.otboo.domain.directmessage.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DirectMessageExceptionTest {

  @Test
  @DisplayName("DM 권한 없음 예외 생성")
  void directMessageForbiddenException_success() {
    DirectMessageForbiddenException exception = new DirectMessageForbiddenException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("DM 잘못된 사용자 예외 생성")
  void directMessageInvalidUserException_success() {
    UUID userId = UUID.randomUUID();

    DirectMessageInvalidUserException exception =
        new DirectMessageInvalidUserException(userId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(userId.toString());
  }

  @Test
  @DisplayName("DM 사용자 없음 예외 생성")
  void directMessageUserNotFoundException_success() {
    UUID userId = UUID.randomUUID();

    DirectMessageUserNotFoundException exception =
        new DirectMessageUserNotFoundException(userId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exception.getMessage()).contains(userId.toString());
  }

  @Test
  @DisplayName("DM 커서 오류 예외 생성")
  void invalidDirectMessageCursorException_success() {
    InvalidDirectMessageCursorException exception =
        new InvalidDirectMessageCursorException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("자기 자신 DM 예외 생성")
  void selfDirectMessageNotAllowedException_success() {
    SelfDirectMessageNotAllowedException exception =
        new SelfDirectMessageNotAllowedException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }
}