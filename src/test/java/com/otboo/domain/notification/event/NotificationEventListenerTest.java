package com.otboo.domain.notification.event;

import static org.mockito.Mockito.verify;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

  @Mock
  private NotificationService notificationService;

  @InjectMocks
  private NotificationEventListener notificationEventListener;

  @Test
  @DisplayName("알림 이벤트를 알림 생성 서비스로 전달한다")
  void handle_success() {
    UUID receiverId = UUID.randomUUID();

    NotificationEvent event = new NotificationEvent(
        receiverId,
        "새 알림",
        "알림 내용",
        NotificationLevel.INFO
    );

    notificationEventListener.handle(event);

    verify(notificationService).createNotification(
        receiverId,
        "새 알림",
        "알림 내용",
        NotificationLevel.INFO
    );
  }
}