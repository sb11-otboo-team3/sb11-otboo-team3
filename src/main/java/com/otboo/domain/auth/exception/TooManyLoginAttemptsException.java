package com.otboo.domain.auth.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class TooManyLoginAttemptsException extends OtbooException {
  public TooManyLoginAttemptsException() {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요."
    );
  }
}