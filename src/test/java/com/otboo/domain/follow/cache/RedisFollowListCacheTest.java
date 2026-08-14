package com.otboo.domain.follow.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.follow.dto.response.FollowDto;
import com.otboo.domain.follow.dto.response.FollowListResponse;
import com.otboo.domain.user.dto.UserSummary;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisFollowListCacheTest {

  private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(
      StringRedisTemplate.class);
  private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(
      ValueOperations.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final RedisFollowListCache cache =
      new RedisFollowListCache(redisTemplate, objectMapper);

  @Test
  @DisplayName("팔로잉 목록 캐시 저장 성공")
  void saveFollowings_success() {
    UUID followerId = UUID.randomUUID();

    FollowListResponse response = createResponse();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);

    cache.saveFollowings(
        followerId,
        null,
        null,
        20,
        null,
        response
    );

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

    verify(valueOperations).set(
        eq("follow:list:followings:" + followerId + ":_:_:20:_"),
        jsonCaptor.capture(),
        eq(Duration.ofMinutes(5))
    );

    assertThat(jsonCaptor.getValue()).contains("\"totalCount\":1");
    assertThat(jsonCaptor.getValue()).contains("\"sortBy\":\"name\"");
    assertThat(jsonCaptor.getValue()).contains("\"sortDirection\":\"ASCENDING\"");
  }

  @Test
  @DisplayName("팔로잉 목록 캐시 조회 성공")
  void findFollowings_success() throws Exception {
    UUID followerId = UUID.randomUUID();

    FollowListResponse response = createResponse();
    String json = objectMapper.writeValueAsString(response);

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("follow:list:followings:" + followerId + ":_:_:20:_"))
        .willReturn(json);

    Optional<FollowListResponse> result = cache.findFollowings(
        followerId,
        null,
        null,
        20,
        null
    );

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(response);
  }

  @Test
  @DisplayName("팔로워 목록 캐시 조회 성공")
  void findFollowers_success() throws Exception {
    UUID followeeId = UUID.randomUUID();

    FollowListResponse response = createResponse();
    String json = objectMapper.writeValueAsString(response);

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("follow:list:followers:" + followeeId + ":_:_:20:_"))
        .willReturn(json);

    Optional<FollowListResponse> result = cache.findFollowers(
        followeeId,
        null,
        null,
        20,
        null
    );

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(response);
  }

  @Test
  @DisplayName("팔로잉 목록 캐시 miss면 Optional.empty 반환")
  void findFollowings_cacheMiss_returnsEmpty() {
    UUID followerId = UUID.randomUUID();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("follow:list:followings:" + followerId + ":_:_:20:_"))
        .willReturn(null);

    Optional<FollowListResponse> result = cache.findFollowings(
        followerId,
        null,
        null,
        20,
        null
    );

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("팔로워 목록 캐시 조회 중 Redis 예외 발생 시 Optional.empty 반환")
  void findFollowers_redisException_returnsEmpty() {
    UUID followeeId = UUID.randomUUID();

    given(redisTemplate.opsForValue())
        .willThrow(new DataAccessResourceFailureException("redis down"));

    Optional<FollowListResponse> result = cache.findFollowers(
        followeeId,
        null,
        null,
        20,
        null
    );

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("팔로잉 목록 캐시 역직렬화 실패 시 Optional.empty 반환")
  void findFollowings_invalidJson_returnsEmpty() {
    UUID followerId = UUID.randomUUID();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("follow:list:followings:" + followerId + ":_:_:20:_"))
        .willReturn("invalid-json");

    Optional<FollowListResponse> result = cache.findFollowings(
        followerId,
        null,
        null,
        20,
        null
    );

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("팔로잉 목록 캐시 키는 cursor, idAfter, nameLike를 정규화한다")
  void saveFollowings_normalizeKey_success() {
    UUID followerId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    FollowListResponse response = createResponse();

    given(redisTemplate.opsForValue()).willReturn(valueOperations);

    cache.saveFollowings(
        followerId,
        "  Alice  ",
        idAfter,
        10,
        "  Bob  ",
        response
    );

    verify(valueOperations).set(
        eq("follow:list:followings:" + followerId + ":alice:" + idAfter + ":10:bob"),
        org.mockito.ArgumentMatchers.any(String.class),
        eq(Duration.ofMinutes(5))
    );
  }

  private FollowListResponse createResponse() {
    UUID followId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();

    FollowDto followDto = new FollowDto(
        followId,
        new UserSummary(followeeId, "followee", null),
        new UserSummary(followerId, "follower", null)
    );

    return new FollowListResponse(
        List.of(followDto),
        null,
        null,
        false,
        1L,
        "name",
        "ASCENDING"
    );
  }
}