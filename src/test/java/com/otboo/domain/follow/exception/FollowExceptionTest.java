package com.otboo.domain.follow.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FollowExceptionTest {

  @Test
  @DisplayName("중복 팔로우 예외 생성")
  void duplicateFollowException_success() {
    DuplicateFollowException exception = new DuplicateFollowException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("팔로우 권한 없음 예외 생성")
  void followForbiddenException_success() {
    FollowForbiddenException exception = new FollowForbiddenException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("팔로우 없음 예외 생성")
  void followNotFoundException_success() {
    UUID followId = UUID.randomUUID();

    FollowNotFoundException exception = new FollowNotFoundException(followId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(followId.toString());
  }

  @Test
  @DisplayName("팔로우 사용자 없음 예외 생성")
  void followUserNotFoundException_success() {
    UUID userId = UUID.randomUUID();

    FollowUserNotFoundException exception = new FollowUserNotFoundException(userId);

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).contains(userId.toString());
  }

  @Test
  @DisplayName("팔로우 커서 오류 예외 생성")
  void invalidFollowCursorException_success() {
    InvalidFollowCursorException exception = new InvalidFollowCursorException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("자기 자신 팔로우 예외 생성")
  void selfFollowNotAllowedException_success() {
    SelfFollowNotAllowedException exception = new SelfFollowNotAllowedException();

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(exception.getMessage()).isNotBlank();
  }
}