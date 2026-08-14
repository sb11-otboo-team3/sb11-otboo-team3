package com.otboo.domain.feed.core.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.feed.core.dto.response.FeedDtoCursorResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisFeedAuthorListCacheTest {

  private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(
      StringRedisTemplate.class);
  private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(
      ValueOperations.class);
  private final SetOperations<String, String> setOperations = org.mockito.Mockito.mock(
      SetOperations.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final RedisFeedAuthorListCache cache =
      new RedisFeedAuthorListCache(redisTemplate, objectMapper);

  @Test
  @DisplayName("작성자 피드 목록 캐시 저장 성공")
  void saveAuthorFeeds_success() {
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    FeedDtoCursorResponse response = createResponse();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(redisTemplate.opsForSet()).willReturn(setOperations);

    cache.saveAuthorFeeds(
        authorId,
        currentUserId,
        null,
        null,
        20,
        response
    );

    String expectedKey = "feed:list:author:" + authorId + ":" + currentUserId
        + ":__OTBOO_NULL__:__OTBOO_NULL__:20";
    String expectedIndexKey = "feed:list:author:index:" + authorId;

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

    verify(valueOperations).set(
        eq(expectedKey),
        jsonCaptor.capture(),
        eq(Duration.ofMinutes(5))
    );

    verify(setOperations).add(expectedIndexKey, expectedKey);
    verify(redisTemplate).expire(expectedIndexKey, Duration.ofMinutes(5));

    assertThat(jsonCaptor.getValue()).contains("\"totalCount\":1");
    assertThat(jsonCaptor.getValue()).contains("\"sortBy\":\"createdAt\"");
    assertThat(jsonCaptor.getValue()).contains("\"sortDirection\":\"DESCENDING\"");
  }

  @Test
  @DisplayName("작성자 피드 목록 캐시 조회 성공")
  void findAuthorFeeds_success() throws Exception {
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    FeedDtoCursorResponse response = createResponse();
    String json = objectMapper.writeValueAsString(response);

    String key = "feed:list:author:" + authorId + ":" + currentUserId
        + ":__OTBOO_NULL__:__OTBOO_NULL__:20";

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get(key)).willReturn(json);

    Optional<FeedDtoCursorResponse> result = cache.findAuthorFeeds(
        authorId,
        currentUserId,
        null,
        null,
        20
    );

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(response);
  }

  @Test
  @DisplayName("작성자 피드 목록 캐시 miss면 Optional.empty 반환")
  void findAuthorFeeds_cacheMiss_returnsEmpty() {
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    String key = "feed:list:author:" + authorId + ":" + currentUserId
        + ":__OTBOO_NULL__:__OTBOO_NULL__:20";

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get(key)).willReturn(null);

    Optional<FeedDtoCursorResponse> result = cache.findAuthorFeeds(
        authorId,
        currentUserId,
        null,
        null,
        20
    );

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("작성자 피드 목록 캐시 조회 중 Redis 예외 발생 시 Optional.empty 반환")
  void findAuthorFeeds_redisException_returnsEmpty() {
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    given(redisTemplate.opsForValue())
        .willThrow(new DataAccessResourceFailureException("redis down"));

    Optional<FeedDtoCursorResponse> result = cache.findAuthorFeeds(
        authorId,
        currentUserId,
        null,
        null,
        20
    );

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("작성자 피드 목록 캐시 역직렬화 실패 시 Optional.empty 반환")
  void findAuthorFeeds_invalidJson_returnsEmpty() {
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    String key = "feed:list:author:" + authorId + ":" + currentUserId
        + ":__OTBOO_NULL__:__OTBOO_NULL__:20";

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get(key)).willReturn("invalid-json");

    Optional<FeedDtoCursorResponse> result = cache.findAuthorFeeds(
        authorId,
        currentUserId,
        null,
        null,
        20
    );

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("작성자 피드 목록 캐시 키는 cursor와 idAfter를 정규화한다")
  void saveAuthorFeeds_normalizeKey_success() {
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    FeedDtoCursorResponse response = createResponse();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(redisTemplate.opsForSet()).willReturn(setOperations);

    cache.saveAuthorFeeds(
        authorId,
        currentUserId,
        "  2026-08-14T00:00:00Z  ",
        idAfter,
        10,
        response
    );

    String expectedKey = "feed:list:author:" + authorId + ":" + currentUserId
        + ":2026-08-14t00%3A00%3A00z:" + idAfter + ":10";

    verify(valueOperations).set(
        eq(expectedKey),
        org.mockito.ArgumentMatchers.any(String.class),
        eq(Duration.ofMinutes(5))
    );
  }

  @Test
  @DisplayName("작성자 피드 목록 캐시 삭제 성공")
  void evictAuthorFeeds_success() {
    UUID authorId = UUID.randomUUID();

    String indexKey = "feed:list:author:index:" + authorId;
    String cacheKey1 = "feed:list:author:" + authorId + ":" + UUID.randomUUID()
        + ":__OTBOO_NULL__:__OTBOO_NULL__:20";
    String cacheKey2 = "feed:list:author:" + authorId + ":" + UUID.randomUUID()
        + ":cursor:idAfter:20";

    given(redisTemplate.opsForSet()).willReturn(setOperations);
    given(setOperations.members(indexKey)).willReturn(Set.of(cacheKey1, cacheKey2));

    cache.evictAuthorFeeds(authorId);

    verify(redisTemplate).delete(Set.of(cacheKey1, cacheKey2));
    verify(redisTemplate).delete(indexKey);
  }

  @Test
  @DisplayName("작성자 피드 목록 캐시 삭제 시 인덱스가 비어 있으면 아무 것도 삭제하지 않는다")
  void evictAuthorFeeds_emptyIndex_doNothing() {
    UUID authorId = UUID.randomUUID();

    String indexKey = "feed:list:author:index:" + authorId;

    given(redisTemplate.opsForSet()).willReturn(setOperations);
    given(setOperations.members(indexKey)).willReturn(Set.of());

    cache.evictAuthorFeeds(authorId);

    verify(setOperations).members(indexKey);
  }

  private FeedDtoCursorResponse createResponse() {
    return new FeedDtoCursorResponse(
        List.of(),
        null,
        null,
        false,
        1L,
        "createdAt",
        "DESCENDING"
    );
  }
}