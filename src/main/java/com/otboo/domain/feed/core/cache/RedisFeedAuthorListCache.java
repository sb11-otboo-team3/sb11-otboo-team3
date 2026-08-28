package com.otboo.domain.feed.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.feed.core.dto.response.FeedDtoCursorResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFeedAuthorListCache implements FeedAuthorListCache {

  private static final String KEY_PREFIX = "feed:list:author:";
  private static final String INDEX_PREFIX = "feed:list:author:index:";
  private static final String DELIMITER = ":";
  private static final String NULL_VALUE = "__OTBOO_NULL__";
  private static final String BLANK_VALUE = "__OTBOO_BLANK__";
  private static final Duration TTL = Duration.ofMinutes(5);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<FeedDtoCursorResponse> findAuthorFeeds(
      UUID authorId,
      UUID currentUserId,
      String cursor,
      UUID idAfter,
      int limit
  ) {
    String key = authorFeedKey(authorId, currentUserId, cursor, idAfter, limit);

    String json;
    try {
      json = redisTemplate.opsForValue().get(key);
    } catch (DataAccessException exception) {
      log.warn(
          "작성자 피드 목록 캐시 조회 실패 - Redis 접근 불가, 캐시 미스로 처리, authorId={}, currentUserId={}",
          authorId,
          currentUserId,
          exception
      );
      return Optional.empty();
    }

    if (json == null) {
      return Optional.empty();
    }

    try {
      return Optional.of(objectMapper.readValue(json, FeedDtoCursorResponse.class));
    } catch (JsonProcessingException exception) {
      log.warn(
          "작성자 피드 목록 캐시 역직렬화 실패 - 캐시 미스로 처리, authorId={}, currentUserId={}",
          authorId,
          currentUserId,
          exception
      );
      return Optional.empty();
    }
  }

  @Override
  public void saveAuthorFeeds(
      UUID authorId,
      UUID currentUserId,
      String cursor,
      UUID idAfter,
      int limit,
      FeedDtoCursorResponse response
  ) {
    String key = authorFeedKey(authorId, currentUserId, cursor, idAfter, limit);

    String json;
    try {
      json = objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException exception) {
      log.warn(
          "작성자 피드 목록 캐시 직렬화 실패 - 캐시 쓰기 생략, authorId={}, currentUserId={}",
          authorId,
          currentUserId,
          exception
      );
      return;
    }

    try {
      redisTemplate.opsForValue().set(key, json, TTL);
      redisTemplate.opsForSet().add(indexKey(authorId), key);
      redisTemplate.expire(indexKey(authorId), TTL);
    } catch (DataAccessException exception) {
      log.warn(
          "작성자 피드 목록 캐시 저장 실패 - Redis 접근 불가, 캐시 쓰기 생략, authorId={}, currentUserId={}",
          authorId,
          currentUserId,
          exception
      );
    }
  }

  @Override
  public void evictAuthorFeeds(UUID authorId) {
    String indexKey = indexKey(authorId);

    try {
      Set<String> keys = redisTemplate.opsForSet().members(indexKey);

      if (keys == null || keys.isEmpty()) {
        return;
      }

      redisTemplate.delete(keys);
      redisTemplate.delete(indexKey);
    } catch (DataAccessException exception) {
      log.warn(
          "작성자 피드 목록 캐시 삭제 실패 - Redis 접근 불가, 캐시 삭제 생략, authorId={}",
          authorId,
          exception
      );
    }
  }

  private String authorFeedKey(
      UUID authorId,
      UUID currentUserId,
      String cursor,
      UUID idAfter,
      int limit
  ) {
    return KEY_PREFIX
        + authorId + DELIMITER
        + currentUserId + DELIMITER
        + normalize(cursor) + DELIMITER
        + normalize(idAfter) + DELIMITER
        + limit;
  }

  private String indexKey(UUID authorId) {
    return INDEX_PREFIX + authorId;
  }

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
}