package com.otboo.domain.directmessage.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DirectMessageUserNotFoundException extends DirectMessageException {

  public DirectMessageUserNotFoundException(UUID userId) {
    super(HttpStatus.NOT_FOUND, "DM 사용자를 찾을 수 없습니다: " + userId);
  }
}