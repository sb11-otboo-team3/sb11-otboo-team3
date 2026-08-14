package com.otboo.domain.follow.cache;

import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import java.util.Optional;
import java.util.UUID;

public interface FollowSummaryCache {

  // Redis 팔로우 요약 캐시 찾기
  Optional<FollowSummaryDto> find(UUID userId, UUID currentUserId);

  // Redis에 저장
  void save(UUID userId, UUID currentUserId, FollowSummaryDto summary);

  // 하나의 캐시를 Redis에서 삭제
  void evict(UUID userId, UUID currentUserId);

  // 특정 사용자와 관련된 팔로우 요약 캐시를 삭제
  void evictRelatedTo(UUID userId);
}
