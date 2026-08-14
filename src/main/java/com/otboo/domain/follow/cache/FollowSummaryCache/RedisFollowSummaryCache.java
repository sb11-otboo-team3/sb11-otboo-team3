package com.otboo.domain.follow.cache.FollowSummaryCache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFollowSummaryCache implements FollowSummaryCache {

  // Redis 구현 클래스
  // 실패해도 log만 찍고 API는 실패하면 안됨
  // key = follow:summary:userId(요약대상):currentUserId(로그인중인 사용자)
  private static final String KEY_PREFIX = "follow:summary:";
  private static final String DELIMITER = ":";
  private static final Duration TTL = Duration.ofMinutes(5); // TTL 5분
  private static final long SCAN_COUNT = 500;

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  // Redis 팔로우 요약 캐시 찾기, 없으면 Optional.empty()
  @Override
  public Optional<FollowSummaryDto> find(UUID userId, UUID currentUserId) {
    String key = followKey(userId, currentUserId);
    String json;
    try {
      // Redis에서 값을 꺼냄
      json = redisTemplate.opsForValue().get(key);
    } catch (DataAccessException exception) {
      log.warn(
          "팔로우 요약 캐시 조회 실패 - Redis 접근 불가, 캐시 미스로 처리, userId={}, currentUserId={}",
          userId,
          currentUserId,
          exception
      );
      return Optional.empty();
    }
    if (json == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(objectMapper.readValue(json, FollowSummaryDto.class));
    } catch (JsonProcessingException exception) {
      log.warn(
          "팔로우 요약 캐시 역직렬화 실패 - 캐시 미스로 처리, userId={}, currentUserId={}",
          userId,
          currentUserId,
          exception
      );
      return Optional.empty();
    }
  }


  // Redis에 저장
  @Override
  public void save(UUID userId, UUID currentUserId, FollowSummaryDto summary) {
    String json;
    try {
      // 객체를 JSON으로 바꾸기
      json = objectMapper.writeValueAsString(summary);
    } catch (JsonProcessingException exception) {
      log.warn(
          "팔로우 요약 캐시 직렬화 실패 - 캐시 쓰기 생략, userId={}, currentUserId={}",
          userId,
          currentUserId,
          exception
      );
      return;
    }
    try {
      redisTemplate.opsForValue().set(followKey(userId, currentUserId), json, TTL);
    } catch (DataAccessException exception) {
      log.warn(
          "팔로우 요약 캐시 저장 실패 - Redis 접근 불가, 캐시 쓰기 생략, userId={}, currentUserId={}",
          userId,
          currentUserId,
          exception
      );
    }
  }

  // 하나의 캐시를 Redis에서 삭제
  @Override
  public void evict(UUID userId, UUID currentUserId) {
    try {
      redisTemplate.delete(followKey(userId, currentUserId));
    } catch (DataAccessException exception) {
      log.warn(
          "팔로우 요약 캐시 삭제 실패 - Redis 접근 불가, 캐시 삭제 생략, userId={}, currentUserId={}",
          userId,
          currentUserId,
          exception
      );
    }
  }

  // 특정 사용자가 요약 대상이거나 조회자인 팔로우 요약 캐시 삭제
  @Override
  public void evictRelatedTo(UUID userId) {
    deleteByPattern(KEY_PREFIX + userId + DELIMITER + "*");
    deleteByPattern(KEY_PREFIX + "*" + DELIMITER + userId);
  }

  private void deleteByPattern(String pattern) {
    try (Cursor<String> cursor = redisTemplate.scan(
        ScanOptions.scanOptions()
            .match(pattern)
            .count(SCAN_COUNT) // 500
            .build()
    )) {
      List<String> keys = new ArrayList<>();
      cursor.forEachRemaining(keys::add);

      // 리스트에 담긴 키 전체 삭제
      if (!keys.isEmpty()) {
        redisTemplate.delete(keys);
      }

    } catch (DataAccessException exception){
      log.warn(
          "팔로우 요약 캐시 패턴 삭제 실패 - Redis 접근 불가, 캐시 삭제 생략, pattern={}",
          pattern,
          exception
      );
    }
  }

  private String followKey(UUID userId, UUID currentUserId) {
    return KEY_PREFIX + userId + DELIMITER + currentUserId;
  }
}
