package com.otboo.domain.notification.outbox;

import static com.otboo.domain.notification.outbox.QNotificationOutbox.notificationOutbox;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;

public class NotificationOutboxRepositoryImpl implements NotificationOutboxRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public NotificationOutboxRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public List<NotificationOutbox> findPendingOrderByCreatedAtAsc(int limit) {
    return queryFactory
        .selectFrom(notificationOutbox)
        .where(notificationOutbox.status.eq(NotificationOutboxStatus.PENDING))
        .orderBy(notificationOutbox.createdAt.asc())
        .limit(limit)
        .fetch();
  }

  @Override
  public long deleteByStatusAndUpdatedAtBefore(
      NotificationOutboxStatus status,
      Instant threshold
  ) {
    return queryFactory
        .delete(notificationOutbox)
        .where(
            notificationOutbox.status.eq(status),
            notificationOutbox.updatedAt.lt(threshold)
        )
        .execute();
  }
}