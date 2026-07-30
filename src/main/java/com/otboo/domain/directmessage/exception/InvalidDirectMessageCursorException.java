package com.otboo.domain.directmessage.exception;

import org.springframework.http.HttpStatus;

public class InvalidDirectMessageCursorException extends DirectMessageException {

  public InvalidDirectMessageCursorException() {
    super(HttpStatus.BAD_REQUEST, "cursor와 idAfter는 함께 요청해야 합니다.");
  }
}