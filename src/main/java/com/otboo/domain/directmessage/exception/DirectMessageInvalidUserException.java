package com.otboo.domain.directmessage.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DirectMessageInvalidUserException extends DirectMessageException {

  public DirectMessageInvalidUserException(UUID userId) {
    super(HttpStatus.BAD_REQUEST, "DM 목록을 조회할 수 없는 사용자입니다: " + userId);
  }
}