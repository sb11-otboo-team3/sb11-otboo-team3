package com.otboo.domain.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

  @Mock
  private NotificationOutboxRepository notificationOutboxRepository;

  @InjectMocks
  private NotificationOutboxService notificationOutboxService;

  @Test
  @DisplayName("알림 이벤트를 Outbox로 저장한다")
  void save_success() {
    UUID receiverId = UUID.randomUUID();

    NotificationEvent event = new NotificationEvent(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.WARNING
    );

    notificationOutboxService.save(event);

    ArgumentCaptor<NotificationOutbox> captor =
        ArgumentCaptor.forClass(NotificationOutbox.class);

    verify(notificationOutboxRepository).save(captor.capture());

    NotificationOutbox saved = captor.getValue();

    assertThat(saved.getReceiverId()).isEqualTo(receiverId);
    assertThat(saved.getTitle()).isEqualTo("알림 제목");
    assertThat(saved.getContent()).isEqualTo("알림 내용");
    assertThat(saved.getLevel()).isEqualTo(NotificationLevel.WARNING);
    assertThat(saved.getOccurredAt()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
    assertThat(saved.getRetryCount()).isZero();
  }
}