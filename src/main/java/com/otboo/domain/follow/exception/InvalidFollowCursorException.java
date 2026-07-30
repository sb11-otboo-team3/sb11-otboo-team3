package com.otboo.domain.follow.exception;

import org.springframework.http.HttpStatus;

public class InvalidFollowCursorException extends FollowException {

  public InvalidFollowCursorException() {
    super(HttpStatus.BAD_REQUEST, "cursor와 idAfter는 함께 요청해야 합니다.");
  }
}