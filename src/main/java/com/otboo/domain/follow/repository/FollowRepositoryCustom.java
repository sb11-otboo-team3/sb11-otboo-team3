package com.otboo.domain.follow.repository;

import com.otboo.domain.follow.entity.Follow;
import java.util.List;
import java.util.UUID;

public interface FollowRepositoryCustom {

  List<Follow> findFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  );

  long countFollowings(UUID followerId, String nameLike);

  List<Follow> findFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  );

  long countFollowers(UUID followeeId, String nameLike);

  // 팔로워 목록 조회(팔로우한 사용자의 피드 등록 알림)
  List<UUID> findFollowerIdsByFolloweeId(UUID followeeId);
}