package com.otboo.domain.notification.outbox;

import com.otboo.domain.notification.event.NotificationEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

  private final NotificationOutboxRepository notificationOutboxRepository;

  @Transactional
  public void save(NotificationEvent event) {
    NotificationOutbox outbox = NotificationOutbox.create(
        event.receiverId(),
        event.title(),
        event.content(),
        event.level(),
        Instant.now()
    );

    notificationOutboxRepository.save(outbox);
  }
}
