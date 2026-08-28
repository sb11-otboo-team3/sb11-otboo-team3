package com.otboo.domain.directmessage.exception;

import org.springframework.http.HttpStatus;

public class SelfDirectMessageNotAllowedException extends DirectMessageException {

  public SelfDirectMessageNotAllowedException() {
    super(HttpStatus.BAD_REQUEST, "자기 자신에게 DM을 보낼 수 없습니다.");
  }
}