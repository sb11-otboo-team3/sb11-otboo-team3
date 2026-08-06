package com.otboo.domain.feed.repository;

import static com.otboo.domain.feed.entity.QFeed.feed;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class FeedRepositoryImpl implements FeedRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public FeedRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public long deleteFeedsDeletedBeforeOneDay() {
    Instant threshold = Instant.now().minus(1, ChronoUnit.DAYS);

    // deletedAt이 찍힌지 1일 이상 지난 피드 물리 삭제
    return queryFactory
        .delete(feed)
        .where(
            feed.deletedAt.isNotNull(),
            feed.deletedAt.loe(threshold)
        )
        .execute();
  }

  @Override
  public long increaseLikeCount(UUID feedId) {
    return queryFactory
        .update(feed)
        // likeCount += 1, 논리삭제 제외
        .set(feed.likeCount, feed.likeCount.add(1))
        .where(
            feed.id.eq(feedId),
            feed.deletedAt.isNull()
        )
        .execute();
  }

  @Override
  public long decreaseLikeCount(UUID feedId) {
    return queryFactory
        .update(feed)
        .set(feed.likeCount, feed.likeCount.subtract(1))
        .where(
            feed.id.eq(feedId),
            feed.deletedAt.isNull(),
            // 0보다 클때만 감소
            feed.likeCount.gt(0)
        )
        .execute();
  }

  @Override
  public long increaseCommentCount(UUID feedId) {
    return queryFactory
        .update(feed)
        // commentCount += 1, 논리삭제 제외
        .set(feed.commentCount, feed.commentCount.add(1))
        .where(
            feed.id.eq(feedId),
            feed.deletedAt.isNull()
        )
        .execute();
  }
}
