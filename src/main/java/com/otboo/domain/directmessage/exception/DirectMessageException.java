package com.otboo.domain.directmessage.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class DirectMessageException extends OtbooException {

  public DirectMessageException(HttpStatus status, String message) {
    super(status, message);
  }
}