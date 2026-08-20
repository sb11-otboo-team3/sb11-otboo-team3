package com.otboo.domain.notification.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

  // 오래된 순으로 200개 가져오기
  List<NotificationOutbox> findTop200ByStatusOrderByCreatedAtAsc(
      NotificationOutboxStatus status
  );
}
