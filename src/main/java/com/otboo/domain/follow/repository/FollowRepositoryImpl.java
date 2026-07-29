package com.otboo.domain.follow.repository;

import static com.otboo.domain.follow.entity.QFollow.follow;

import com.otboo.domain.follow.entity.Follow;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepositoryCustom {

  private final JPAQueryFactory queryFactory;

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
            cursorCondition(cursor, idAfter)
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

    return count == null ? 0L : count;
  }

  private BooleanExpression followeeNameContains(String nameLike) {
    if (nameLike == null || nameLike.isBlank()) {
      return null;
    }

    return follow.followee.name.containsIgnoreCase(nameLike.trim());
  }

  private BooleanExpression cursorCondition(String cursor, UUID idAfter) {
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
}