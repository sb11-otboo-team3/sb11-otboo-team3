package com.otboo.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.auth.jwt.JwtProperties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private RefreshTokenService refreshTokenService;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties(
        "test-secret-key-for-refresh-token-test-minimum-256-bits",
        900000L,
        604800000L
    );
    refreshTokenService = new RefreshTokenService(redisTemplate, jwtProperties);
  }

  @Test
  @DisplayName("발급하면 UUID 형식의 토큰 문자열을 반환한다")
  void issueReturnsUuidFormattedToken() throws Exception {
    // given
    given(redisTemplate.opsForValue()).willReturn(valueOperations);

    // when
    String token = refreshTokenService.issue(UUID.randomUUID(), 0L);

    // then
    assertThat(token).matches(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    verify(valueOperations).set(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("존재하는 토큰을 소비하면 userId와 tokenVersion을 반환하고 삭제된다")
  void consumeTokenInfoReturnsInfoAndDeletesWhenTokenExists() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    long tokenVersion = 3L;
    String token = "some-refresh-token";
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.getAndDelete("refresh:" + token))
        .willReturn(userId + ":" + tokenVersion);
    // when
    Optional<RefreshTokenService.TokenInfo> result = refreshTokenService.consumeTokenInfo(token);
    // then
    assertThat(result).isPresent();
    assertThat(result.get().userId()).isEqualTo(userId);
    assertThat(result.get().tokenVersion()).isEqualTo(tokenVersion);
  }
  @Test
  @DisplayName("존재하지 않는 토큰을 소비하려 하면 빈 값을 반환한다")
  void consumeTokenInfoReturnsEmptyWhenTokenNotFound() throws Exception {
    // given
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.getAndDelete("refresh:invalid-token")).willReturn(null);
    // when
    Optional<RefreshTokenService.TokenInfo> result =
        refreshTokenService.consumeTokenInfo("invalid-token");
    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("이전 형식(userId만 저장된) 값을 소비하면 빈 값을 반환한다")
  void consumeTokenInfoReturnsEmptyWhenValueIsLegacyFormat() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.getAndDelete("refresh:legacy-token"))
        .willReturn(userId.toString()); // 콜론 없이 userId만 저장된 예전 형식

    // when
    Optional<RefreshTokenService.TokenInfo> result =
        refreshTokenService.consumeTokenInfo("legacy-token");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("손상된(파싱 불가능한) 값을 소비하면 빈 값을 반환한다")
  void consumeTokenInfoReturnsEmptyWhenValueIsCorrupted() throws Exception {
    // given
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.getAndDelete("refresh:corrupted-token"))
        .willReturn("not-a-valid-uuid:not-a-number");

    // when
    Optional<RefreshTokenService.TokenInfo> result =
        refreshTokenService.consumeTokenInfo("corrupted-token");

    // then
    assertThat(result).isEmpty();
  }
}