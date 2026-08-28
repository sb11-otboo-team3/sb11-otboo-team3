package com.otboo.domain.user.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidUserCursorException extends OtbooException {
  public InvalidUserCursorException() {
    super(HttpStatus.BAD_REQUEST, "계정 목록 커서 요청이 올바르지 않습니다.");
  }
}