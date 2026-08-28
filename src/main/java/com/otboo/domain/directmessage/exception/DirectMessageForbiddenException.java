package com.otboo.domain.directmessage.exception;

import org.springframework.http.HttpStatus;

public class DirectMessageForbiddenException extends DirectMessageException {

  public DirectMessageForbiddenException() {
    super(HttpStatus.FORBIDDEN, "DM을 처리할 권한이 없습니다.");
  }
}