package com.otboo.domain.feed.repository;

import static com.otboo.domain.feed.entity.QFeed.feed;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class FeedRepositoryImpl implements FeedRepositoryCustom{

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
}
