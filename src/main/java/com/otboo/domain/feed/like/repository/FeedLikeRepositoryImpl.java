package com.otboo.domain.feed.like.repository;

import static com.otboo.domain.feed.like.entity.QFeedLike.feedLike;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.UUID;

public class FeedLikeRepositoryImpl implements FeedLikeRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public FeedLikeRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public long deleteByFeedIdAndUserId(UUID feedId, UUID userId) {
    return queryFactory
        .delete(feedLike)
        .where(
            feedLike.feed.id.eq(feedId),
            feedLike.user.id.eq(userId)
        )
        .execute();
  }
}