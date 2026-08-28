package com.otboo.domain.feed.comment.repository;

import static com.otboo.domain.feed.comment.entity.QComment.comment;

import com.otboo.domain.feed.comment.entity.Comment;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class FeedCommentRepositoryImpl implements FeedCommentRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public FeedCommentRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public List<Comment> findComments(
      UUID feedId,
      String cursor,
      UUID idAfter,
      int limit
  ) {
    return queryFactory
        .selectFrom(comment)
        .join(comment.feed).fetchJoin()
        .leftJoin(comment.author).fetchJoin()
        .where(
            comment.feed.deletedAt.isNull(),
            comment.feed.id.eq(feedId),
            commentCursorCondition(cursor, idAfter)
        )
        .orderBy(
            comment.createdAt.asc(),
            comment.id.asc()
        )
        .limit(limit)
        .fetch();
  }

  private BooleanExpression commentCursorCondition(String cursor, UUID idAfter) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    Instant cursorCreatedAt = Instant.parse(cursor);

    return Expressions.booleanTemplate(
        "({0}, {1}) > ({2}, {3})",
        comment.createdAt, comment.id, cursorCreatedAt, idAfter
    );
  }

  @Override
  public long countComments(UUID feedId) {
    Long count = queryFactory
        .select(comment.count())
        .from(comment)
        .where(
            comment.feed.id.eq(feedId),
            comment.feed.deletedAt.isNull()
        )
        .fetchOne();

    return count == null ? 0 : count;
  }
}
