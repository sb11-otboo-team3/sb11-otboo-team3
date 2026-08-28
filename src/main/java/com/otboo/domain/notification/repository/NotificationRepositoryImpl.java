package com.otboo.domain.notification.repository;

import static com.otboo.domain.notification.entity.QNotification.notification;

import com.otboo.domain.notification.entity.Notification;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class NotificationRepositoryImpl implements NotificationRepositoryCustom{

  private final JPAQueryFactory queryFactory;

  public NotificationRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public List<Notification> findNotifications(
      UUID receiverId,
      String cursor,
      UUID idAfter,
      int limit
  ){
    return queryFactory
        .selectFrom(notification)
        .join(notification.receiver).fetchJoin()
        .where(
            notification.receiver.id.eq(receiverId),
            notificationCursorCondition(cursor, idAfter)
        )
        .orderBy(
            notification.createdAt.desc(),
            notification.id.desc()
        )
        .limit(limit)
        .fetch();
  }

  private BooleanExpression notificationCursorCondition(String cursor, UUID idAfter) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    Instant cursorCreatedAt = Instant.parse(cursor);

    return Expressions.booleanTemplate(
        "({0}, {1}) < ({2}, {3})",
        notification.createdAt, notification.id, cursorCreatedAt, idAfter
    );
  }

  @Override
  public long countNotifications(UUID receiverId) {
    Long count = queryFactory
        .select(notification.count())
        .from(notification)
        .where(notification.receiver.id.eq(receiverId))
        .fetchOne();

    return count == null ? 0 : count;
  }

  @Override
  public List<Notification> findNotificationsAfter(UUID receiverId, UUID lastEventId, int limit){
    Notification lastNotification = queryFactory
        .selectFrom(notification)
        .where(
            notification.id.eq(lastEventId),
            notification.receiver.id.eq(receiverId)
        )
        .fetchOne();

    if (lastNotification == null) {
      return List.of();
    }

    return queryFactory
        .selectFrom(notification)
        .join(notification.receiver).fetchJoin()
        .where(
            notification.receiver.id.eq(receiverId),
            Expressions.booleanTemplate(
                "({0}, {1}) > ({2}, {3})",
                notification.createdAt, notification.id,
                lastNotification.getCreatedAt(), lastEventId
            )
        )
        .orderBy(
            notification.createdAt.asc(),
            notification.id.asc()
        )
        .limit(limit)
        .fetch();
  }
}
