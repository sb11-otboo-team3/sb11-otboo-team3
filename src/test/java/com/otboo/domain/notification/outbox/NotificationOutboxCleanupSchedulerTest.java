package com.otboo.domain.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxCleanupSchedulerTest {

  @Mock
  private NotificationOutboxRepository notificationOutboxRepository;

  @InjectMocks
  private NotificationOutboxCleanupScheduler notificationOutboxCleanupScheduler;

  @Test
  @DisplayName("PUBLISHED와 FAILED Outbox만 보관 기간 기준으로 삭제한다")
  void cleanup_success() {
    Instant before = Instant.now();

    notificationOutboxCleanupScheduler.cleanup();

    Instant after = Instant.now();

    ArgumentCaptor<Instant> publishedThresholdCaptor =
        ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> failedThresholdCaptor =
        ArgumentCaptor.forClass(Instant.class);

    verify(notificationOutboxRepository).deleteByStatusAndUpdatedAtBefore(
        eq(NotificationOutboxStatus.PUBLISHED),
        publishedThresholdCaptor.capture()
    );

    verify(notificationOutboxRepository).deleteByStatusAndUpdatedAtBefore(
        eq(NotificationOutboxStatus.FAILED),
        failedThresholdCaptor.capture()
    );

    Instant publishedThreshold = publishedThresholdCaptor.getValue();
    Instant failedThreshold = failedThresholdCaptor.getValue();

    assertThat(publishedThreshold)
        .isBetween(
            before.minus(Duration.ofDays(1)),
            after.minus(Duration.ofDays(1))
        );

    assertThat(failedThreshold)
        .isBetween(
            before.minus(Duration.ofDays(7)),
            after.minus(Duration.ofDays(7))
        );
  }
}