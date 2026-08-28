package com.otboo.domain.follow.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
public class RedisFollowListCache implements FollowListCache{

  private static final String FOLLOWINGS_KEY_PREFIX = "follow:list:followings:";
  private static final String FOLLOWERS_KEY_PREFIX = "follow:list:followers:";
  private static final String DELIMITER = ":";
  private static final String NULL_VALUE = "__OTBOO_NULL__";
  private static final String BLANK_VALUE = "__OTBOO_BLANK__";
  private static final Duration TTL = Duration.ofMinutes(5); // TTL 5분
  private static final long SCAN_COUNT = 500;

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  // 내가 팔로우하는 사람 목록
  @Override
  public Optional<FollowListResponse> findFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ){
    return find(followingsKey(followerId, cursor, idAfter, limit, nameLike));
  }

  // 팔로잉 목록 응답 Redis에 저장
  @Override
  public void saveFollowings(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike,
      FollowListResponse response
  ){
    save(followingsKey(followerId, cursor, idAfter, limit, nameLike), response);
  }

  // 나를 팔로우하는 사람 목록
  @Override
  public Optional<FollowListResponse> findFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ){
    return find(followersKey(followeeId, cursor, idAfter, limit, nameLike));
  }

  // 팔로워 목록 응답 Redis에 저장
  @Override
  public void saveFollowers(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike,
      FollowListResponse response
  ){
    save(followersKey(followeeId, cursor, idAfter, limit, nameLike), response);
  }

  // 특정 사용자 팔로잉 목록 캐시 삭제
  @Override
  public void evictFollowings(UUID followerId){
    deleteByPattern(FOLLOWINGS_KEY_PREFIX + followerId + DELIMITER + "*");
  }

  // 특정 사용자 팔로워 목록 캐시 삭제
  @Override
  public void evictFollowers(UUID followeeId){
    deleteByPattern(FOLLOWERS_KEY_PREFIX + followeeId + DELIMITER + "*");
  }

  private Optional<FollowListResponse> find(String key) {
    String json;
    try {
      json = redisTemplate.opsForValue().get(key);
    } catch (DataAccessException exception) {
      log.warn(
          "팔로우 목록 캐시 조회 실패 - Redis 접근 불가, 캐시 미스로 처리, key={}",
          key,
          exception
      );
      return Optional.empty();
    }

    if (json == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(objectMapper.readValue(json, FollowListResponse.class));
    } catch (JsonProcessingException exception) {
      log.warn(
          "팔로우 목록 캐시 역직렬화 실패 - 캐시 미스로 처리, key={}",
          key,
          exception
      );
      return Optional.empty();
    }
  }

  private void save(String key, FollowListResponse response) {
    String json;
    try {
      json = objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException exception) {
      log.warn(
          "팔로우 목록 캐시 직렬화 실패 - 캐시 쓰기 생략, key={}",
          key,
          exception
      );
      return;
    }

    try {
      redisTemplate.opsForValue().set(key, json, TTL);
    } catch (DataAccessException exception) {
      log.warn(
          "팔로우 목록 캐시 저장 실패 - Redis 접근 불가, 캐시 쓰기 생략, key={}",
          key,
          exception
      );
    }
  }

  private String followingsKey(
      UUID followerId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ) {
    return FOLLOWINGS_KEY_PREFIX
        + followerId + DELIMITER
        + normalize(cursor) + DELIMITER
        + normalize(idAfter) + DELIMITER
        + limit + DELIMITER
        + normalize(nameLike);
  }

  private String followersKey(
      UUID followeeId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike
  ) {
    return FOLLOWERS_KEY_PREFIX
        + followeeId + DELIMITER
        + normalize(cursor) + DELIMITER
        + normalize(idAfter) + DELIMITER
        + limit + DELIMITER
        + normalize(nameLike);
  }

  // cursor/idAfter/nameLike가 null, blank 일때 NULL_VALUE 삽입
  // 앞뒤 공백 제거, 소문자로 변환
  private String normalize(Object value) {
    if (value == null) {
      return NULL_VALUE;
    }

    String stringValue = value.toString();

    if (stringValue.isBlank()) {
      return BLANK_VALUE;
    }

    return URLEncoder.encode(
        stringValue.trim().toLowerCase(),
        StandardCharsets.UTF_8
    );
  }

  private void deleteByPattern(String pattern) {
    try (Cursor<String> cursor = redisTemplate.scan(
        ScanOptions.scanOptions()
            .match(pattern)
            .count(SCAN_COUNT)
            .build()
    )) {
      List<String> keys = new ArrayList<>();
      cursor.forEachRemaining(keys::add);

      if (!keys.isEmpty()) {
        redisTemplate.delete(keys);
      }
    } catch (DataAccessException exception) {
      log.warn(
          "팔로우 목록 캐시 패턴 삭제 실패 - Redis 접근 불가, 캐시 삭제 생략, pattern={}",
          pattern,
          exception
      );
    }
  }
}
