package com.otboo.domain.notification.controller;

import com.otboo.domain.notification.controller.docs.DeleteNotificationApi;
import com.otboo.domain.notification.controller.docs.GetNotificationsApi;
import com.otboo.domain.notification.controller.docs.NotificationApi;
import com.otboo.domain.notification.dto.response.NotificationDtoCursorResponse;
import com.otboo.domain.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NotificationApi
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  @GetNotificationsApi
  @GetMapping
  public ResponseEntity<NotificationDtoCursorResponse> getNotifications(
      @RequestParam (required = false) String cursor,
      @RequestParam (required = false) UUID idAfter,
      @RequestParam  @Min(value = 1, message = "limit는 1 이상이어야 합니다.") @Max(value = 100, message = "limit는 100 이하여야 합니다.") int limit,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();

    NotificationDtoCursorResponse response = notificationService.getNotifications(
        cursor, idAfter, limit, currentUserId
    );

    return ResponseEntity.ok(response);
  }

  @DeleteNotificationApi
  @DeleteMapping("/{notificationId}")
  public ResponseEntity<Void> deleteNotification(
      @PathVariable UUID notificationId,
      Authentication authentication
  ) {
    UUID currentUserId = (UUID) authentication.getPrincipal();
    notificationService.deleteNotification(notificationId, currentUserId);
    return ResponseEntity.noContent().build();
  }
}
