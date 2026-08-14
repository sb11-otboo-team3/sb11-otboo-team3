package com.otboo.domain.follow.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.follow.dto.response.FollowSummaryDto;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisFollowSummaryCacheTest {

  private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
  private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final RedisFollowSummaryCache cache =
      new RedisFollowSummaryCache(redisTemplate, objectMapper);

  @Test
  @DisplayName("팔로우 요약 캐시 저장 성공")
  void save_success() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    FollowSummaryDto summary = new FollowSummaryDto(
        userId,
        10L,
        3L,
        true,
        UUID.randomUUID(),
        false
    );

    given(redisTemplate.opsForValue()).willReturn(valueOperations);

    cache.save(userId, currentUserId, summary);

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

    verify(valueOperations).set(
        eq("follow:summary:" + userId + ":" + currentUserId),
        jsonCaptor.capture(),
        eq(Duration.ofMinutes(5))
    );

    assertThat(jsonCaptor.getValue()).contains(userId.toString());
    assertThat(jsonCaptor.getValue()).contains("\"followerCount\":10");
    assertThat(jsonCaptor.getValue()).contains("\"followingCount\":3");
  }

  @Test
  @DisplayName("팔로우 요약 캐시 조회 성공")
  void find_success() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID followedByMeId = UUID.randomUUID();

    FollowSummaryDto summary = new FollowSummaryDto(
        userId,
        10L,
        3L,
        true,
        followedByMeId,
        false
    );

    String json = objectMapper.writeValueAsString(summary);

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("follow:summary:" + userId + ":" + currentUserId))
        .willReturn(json);

    Optional<FollowSummaryDto> result = cache.find(userId, currentUserId);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(summary);
  }

  @Test
  @DisplayName("팔로우 요약 캐시 miss면 Optional.empty 반환")
  void find_cacheMiss_returnsEmpty() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("follow:summary:" + userId + ":" + currentUserId))
        .willReturn(null);

    Optional<FollowSummaryDto> result = cache.find(userId, currentUserId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("팔로우 요약 캐시 조회 중 Redis 예외 발생 시 Optional.empty 반환")
  void find_redisException_returnsEmpty() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    given(redisTemplate.opsForValue()).willThrow(new DataAccessResourceFailureException("redis down"));

    Optional<FollowSummaryDto> result = cache.find(userId, currentUserId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("팔로우 요약 캐시 역직렬화 실패 시 Optional.empty 반환")
  void find_invalidJson_returnsEmpty() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("follow:summary:" + userId + ":" + currentUserId))
        .willReturn("invalid-json");

    Optional<FollowSummaryDto> result = cache.find(userId, currentUserId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("팔로우 요약 캐시 단건 삭제 성공")
  void evict_success() {
    UUID userId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    cache.evict(userId, currentUserId);

    verify(redisTemplate).delete("follow:summary:" + userId + ":" + currentUserId);
  }
}