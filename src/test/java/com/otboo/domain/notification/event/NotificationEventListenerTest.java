package com.otboo.domain.notification.event;

import static org.mockito.Mockito.verify;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.outbox.NotificationOutboxService;
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
  private NotificationOutboxService notificationOutboxService;

  @InjectMocks
  private NotificationEventListener notificationEventListener;

  @Test
  @DisplayName("알림 이벤트를 Outbox 저장 서비스로 전달한다")
  void handle_success() {
    NotificationEvent event = new NotificationEvent(
        UUID.randomUUID(),
        "새 알림",
        "알림 내용",
        NotificationLevel.INFO
    );

    notificationEventListener.handle(event);

    verify(notificationOutboxService).save(event);
  }
}