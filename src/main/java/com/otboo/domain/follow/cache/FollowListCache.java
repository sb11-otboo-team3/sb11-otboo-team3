package com.otboo.domain.follow.cache;

import com.otboo.domain.follow.dto.response.FollowListResponse;
import java.util.Optional;
import java.util.UUID;

public interface FollowListCache {

  // 내가 팔로우하는 사람 목록
  Optional<FollowListResponse> findFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  );

  // 팔로잉 목록 응답 Redis에 저장
  void saveFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike,
      FollowListResponse response
  );

  // 나를 팔로우하는 사람 목록
  Optional<FollowListResponse> findFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  );

  // 팔로워 목록 응답 Redis에 저장
  void saveFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike,
      FollowListResponse response
  );

  // 특정 사용자 팔로잉 목록 캐시 삭제
  void evictFollowings(UUID followerId);

  // 특정 사용자 팔로워 목록 캐시 삭제
  void evictFollowers(UUID followeeId);
}