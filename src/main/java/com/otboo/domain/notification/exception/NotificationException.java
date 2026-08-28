package com.otboo.domain.notification.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

public class NotificationException extends OtbooException {

  public NotificationException(HttpStatus status, String message) {
    super(status, message);
  }
}