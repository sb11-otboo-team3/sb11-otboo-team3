package com.otboo.domain.directmessage.repository;

import static com.otboo.domain.directmessage.entity.QDirectMessage.directMessage;

import com.otboo.domain.directmessage.entity.DirectMessage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DirectMessageRepositoryImpl implements DirectMessageRepositoryCustom{

  private final JPAQueryFactory queryFactory;

  public DirectMessageRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  // 특정 dmKey를 사용하는 방의 메세지를 가져오는 메서드
  @Override
  public List<DirectMessage> findDirectMessages(
      String dmKey,
      String cursor,
      UUID idAfter,
      int limit
  ){
    return queryFactory
        .selectFrom(directMessage)
        // user가 null이 되는 상황 발생 가능하기에 message기준으로 맞추기
        .leftJoin(directMessage.sender).fetchJoin()
        .leftJoin(directMessage.receiver).fetchJoin()
        .where(
            directMessage.dmKey.eq(dmKey),
            directMessageCursorCondition(cursor, idAfter)
        )
        .orderBy(
            directMessage.createdAt.desc(),
            directMessage.id.desc()
        )
        .limit(limit)
        .fetch();
  }

  // 특정 dmKey를 사용하는 방의 메세지 개수를 세는 메서드
  @Override
  public long countDirectMessages(String dmKey) {
    Long count = queryFactory
        .select(directMessage.count())
        .from(directMessage)
        .where(directMessage.dmKey.eq(dmKey))
        .fetchOne();

    // message 개수 결과가 null이면 0반환
    return count == null ? 0 : count;
  }

  private BooleanExpression directMessageCursorCondition(String cursor, UUID idAfter) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    Instant cursorCreatedAt = Instant.parse(cursor);

    return directMessage.createdAt.lt(cursorCreatedAt)
        .or(
            directMessage.createdAt.eq(cursorCreatedAt)
                .and(directMessage.id.lt(idAfter))
        );
  }

  @Override
  public long deleteMessages(){
    return queryFactory
        .delete(directMessage)
        .where(
            directMessage.sender.isNull(),
            directMessage.receiver.isNull()
        )
        .execute();
  }
}
