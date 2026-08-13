package com.otboo.domain.notification.event;

import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

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
  @DisplayName("들어온 이벤트를 서비스의 create로 생성하게 넘기기 테스트")
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

  @Test
  @DisplayName("알림 이벤트 처리 중 예외가 발생해도 밖으로 전파하지 않는다")
  void handle_exception_doesNotThrow() {
    UUID receiverId = UUID.randomUUID();

    NotificationEvent event = new NotificationEvent(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO
    );

    willThrow(new RuntimeException("알림 생성 실패"))
        .given(notificationService)
        .createNotification(
            receiverId,
            "알림 제목",
            "알림 내용",
            NotificationLevel.INFO
        );

    assertThatCode(() -> notificationEventListener.handle(event))
        .doesNotThrowAnyException();

    verify(notificationService).createNotification(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO
    );
  }
}