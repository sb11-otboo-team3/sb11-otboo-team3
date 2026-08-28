package com.otboo.domain.notification.outbox;

import java.time.Instant;
import java.util.List;

public interface NotificationOutboxRepositoryCustom {

  List<NotificationOutbox> findPendingOrderByCreatedAtAsc(int limit);

  long deleteByStatusAndUpdatedAtBefore(
      NotificationOutboxStatus status,
      Instant threshold
  );
}
