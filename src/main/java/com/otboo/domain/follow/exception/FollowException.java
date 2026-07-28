package com.otboo.domain.follow.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public abstract class FollowException extends OtbooException {

  protected FollowException(HttpStatus status, String message) {
    super(status, message);
  }
}