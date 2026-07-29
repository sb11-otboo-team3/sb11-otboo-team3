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

}