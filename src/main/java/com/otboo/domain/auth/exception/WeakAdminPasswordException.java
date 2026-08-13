package com.otboo.domain.auth.exception;

import com.otboo.global.error.OtbooException;
import com.otboo.global.validation.StrongPasswordPolicy;
import org.springframework.http.HttpStatus;

public class WeakAdminPasswordException extends OtbooException {
  public WeakAdminPasswordException() {
    super(HttpStatus.BAD_REQUEST, "ADMIN 계정은 " + StrongPasswordPolicy.DESCRIPTION);
  }
}