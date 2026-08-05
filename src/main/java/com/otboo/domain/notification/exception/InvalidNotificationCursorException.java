package com.otboo.domain.notification.exception;

import org.springframework.http.HttpStatus;

public class InvalidNotificationCursorException extends NotificationException {

  public InvalidNotificationCursorException() {
    super(HttpStatus.BAD_REQUEST, "알림 커서 요청이 올바르지 않습니다.");
  }
}