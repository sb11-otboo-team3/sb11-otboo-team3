package com.otboo.domain.notification.exception;

import org.springframework.http.HttpStatus;

public class NotificationForbiddenException extends NotificationException {

  public NotificationForbiddenException() {
    super(HttpStatus.BAD_REQUEST, "해당 알림을 처리할 권한이 없습니다.");
  }
}