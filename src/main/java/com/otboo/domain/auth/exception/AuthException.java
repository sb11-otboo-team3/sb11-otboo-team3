package com.otboo.domain.auth.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public abstract class AuthException extends OtbooException {

  protected AuthException(HttpStatus status, String message) {
    super(status, message);
  }
}