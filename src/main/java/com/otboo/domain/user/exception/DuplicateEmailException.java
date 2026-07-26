package com.otboo.domain.user.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends OtbooException {

  public DuplicateEmailException(String email) {
    super(HttpStatus.CONFLICT, "이미 등록된 이메일입니다: " + email);
  }
}