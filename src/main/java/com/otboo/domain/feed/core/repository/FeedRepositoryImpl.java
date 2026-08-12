package com.otboo.domain.feed.core.repository;

import static com.otboo.domain.feed.core.entity.QFeed.feed;

import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

  @Override
  public List<Feed> findFeeds(
      String cursor,
      UUID idAfter,
      int limit,
      SortBy sortBy,
      SortDirection sortDirection,
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual
  ) {
    return queryFactory
        .selectFrom(feed)
        .join(feed.author).fetchJoin()
        .join(feed.weather).fetchJoin()
        .where(
            feed.deletedAt.isNull(),
            keywordLike(keywordLike),
            skyStatusEqual(skyStatusEqual),
            precipitationTypeEqual(precipitationTypeEqual),
            authorIdEqual(authorIdEqual),
            feedCursorCondition(cursor, idAfter, sortBy, sortDirection)
        )
        .orderBy(
            feedOrder(sortBy, sortDirection),
            idOrder(sortDirection)
        )
        .limit(limit)
        .fetch();
  }

  @Override
  public long countFeeds(
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual
  ) {
    Long count = queryFactory
        .select(feed.count())
        .from(feed)
        .where(
            feed.deletedAt.isNull(),
            keywordLike(keywordLike),
            skyStatusEqual(skyStatusEqual),
            precipitationTypeEqual(precipitationTypeEqual),
            authorIdEqual(authorIdEqual)
        )
        .fetchOne();

    return count == null ? 0 : count;
  }


  // 피드 본문에 검색어가 포함된 피드만 조회
  private BooleanExpression keywordLike(String keywordLike) {
    if (keywordLike == null || keywordLike.isBlank()) {
      return null;
    }

    return feed.content.containsIgnoreCase(keywordLike.trim());
  }

  // 하늘 상태에 해당하는 피드만 조회
  private BooleanExpression skyStatusEqual(SkyStatus skyStatusEqual) {
    if (skyStatusEqual == null) {
      return null;
    }

    return feed.weather.skyStatus.eq(skyStatusEqual);
  }

  // 날씨상태에 해당하는 피드만 조회
  private BooleanExpression precipitationTypeEqual(PrecipitationType precipitationTypeEqual) {
    if (precipitationTypeEqual == null) {
      return null;
    }

    return feed.weather.precipitationType.eq(precipitationTypeEqual);
  }

  // 특정 사용자가 작성한 피드만 조회
  private BooleanExpression authorIdEqual(UUID authorIdEqual) {
    if (authorIdEqual == null) {
      return null;
    }

    return feed.author.id.eq(authorIdEqual);
  }

  private BooleanExpression feedCursorCondition(
      String cursor,
      UUID idAfter,
      SortBy sortBy,
      SortDirection sortDirection
  ) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    if (sortBy == SortBy.createdAt) {
      Instant cursorCreatedAt = Instant.parse(cursor);

      if (sortDirection == SortDirection.ASCENDING) {
        return feed.createdAt.gt(cursorCreatedAt)
            .or(feed.createdAt.eq(cursorCreatedAt).and(feed.id.gt(idAfter)));
      }

      return feed.createdAt.lt(cursorCreatedAt)
          .or(feed.createdAt.eq(cursorCreatedAt).and(feed.id.lt(idAfter)));
    }

    long cursorLikeCount = Long.parseLong(cursor);

    if (sortDirection == SortDirection.ASCENDING) {
      return feed.likeCount.gt(cursorLikeCount)
          .or(feed.likeCount.eq(cursorLikeCount).and(feed.id.gt(idAfter)));
    }

    return feed.likeCount.lt(cursorLikeCount)
        .or(feed.likeCount.eq(cursorLikeCount).and(feed.id.lt(idAfter)));
  }

  private OrderSpecifier<?> feedOrder(SortBy sortBy, SortDirection sortDirection) {
    boolean ascending = sortDirection == SortDirection.ASCENDING;

    if (sortBy == SortBy.likeCount) {
      return ascending ? feed.likeCount.asc() : feed.likeCount.desc();
    }

    return ascending ? feed.createdAt.asc() : feed.createdAt.desc();
  }

  private OrderSpecifier<?> idOrder(SortDirection sortDirection) {
    if (sortDirection == SortDirection.ASCENDING) {
      return feed.id.asc();
    }

    return feed.id.desc();
  }
}