package com.otboo.domain.follow.repository;

import static com.otboo.domain.follow.entity.QFollow.follow;

import com.otboo.domain.follow.entity.Follow;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;

public class FollowRepositoryImpl implements FollowRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public FollowRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public List<Follow> findFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ) {
    return queryFactory
        .selectFrom(follow)
        .join(follow.followee).fetchJoin()
        .join(follow.follower).fetchJoin()
        .where(
            // followerId가 팔로우중인 id들 가져오기
            follow.follower.id.eq(followerId),
            followeeNameContains(nameLike),
            followeeCursorCondition(cursor, idAfter)
        )
        .orderBy(follow.followee.name.lower().asc(), follow.id.asc())
        .limit(limit)
        .fetch();
  }

  @Override
  public long countFollowings(UUID followerId, String nameLike) {
    Long count = queryFactory
        .select(follow.count())
        .from(follow)
        .join(follow.followee)
        .where(
            // followerId가 팔로우하는 전체 수
            follow.follower.id.eq(followerId),
            followeeNameContains(nameLike)
        )
        .fetchOne();

    // 비어있는 경우 0
    return count == null ? 0 : count;
  }

  private BooleanExpression followeeNameContains(String nameLike) {
    if (nameLike == null || nameLike.isBlank()) {
      return null;
    }

    return follow.followee.name.containsIgnoreCase(nameLike.trim());
  }

  private BooleanExpression followeeCursorCondition(String cursor, UUID idAfter) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    String normalizedCursor = cursor.trim().toLowerCase();

    return follow.followee.name.lower().gt(normalizedCursor)
        .or(
            follow.followee.name.lower().eq(normalizedCursor)
                .and(follow.id.gt(idAfter))
        );
  }

  @Override
  public List<Follow> findFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ) {
    return queryFactory
        .selectFrom(follow)
        .join(follow.followee).fetchJoin()
        .join(follow.follower).fetchJoin()
        .where(
            // followerId가 팔로잉당하는중인 id들 가져오기
            follow.followee.id.eq(followeeId),
            followerNameContains(nameLike),
            followerCursorCondition(cursor, idAfter)
        )
        .orderBy(follow.follower.name.lower().asc(), follow.id.asc())
        .limit(limit)
        .fetch();
  }

  @Override
  public long countFollowers(UUID followeeId, String nameLike) {
    Long count = queryFactory
        .select(follow.count())
        .from(follow)
        .join(follow.follower)
        .where(
            follow.followee.id.eq(followeeId),
            followerNameContains(nameLike)
        )
        .fetchOne();

    // 비어있는 경우 0
    return count == null ? 0 : count;
  }

  private BooleanExpression followerNameContains(String nameLike) {
    if (nameLike == null || nameLike.isBlank()) {
      return null;
    }

    return follow.follower.name.containsIgnoreCase(nameLike.trim());
  }

  private BooleanExpression followerCursorCondition(String cursor, UUID idAfter) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return null;
    }

    String normalizedCursor = cursor.trim().toLowerCase();

    return follow.follower.name.lower().gt(normalizedCursor)
        .or(
            follow.follower.name.lower().eq(normalizedCursor)
                .and(follow.id.gt(idAfter))
        );
  }

}