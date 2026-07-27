package com.otboo.domain.auth.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends OtbooException {

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
  }
}