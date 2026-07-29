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
    String token = refreshTokenService.issue(UUID.randomUUID());

    // then
    assertThat(token).matches(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    verify(valueOperations).set(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("존재하는 토큰으로 조회하면 userId를 반환한다")
  void findUserIdReturnsUserIdWhenTokenExists() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    String token = "some-refresh-token";
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("refresh:" + token)).willReturn(userId.toString());

    // when
    Optional<UUID> result = refreshTokenService.findUserId(token);

    // then
    assertThat(result).contains(userId);
  }

  @Test
  @DisplayName("존재하지 않는 토큰으로 조회하면 빈 값을 반환한다")
  void findUserIdReturnsEmptyWhenTokenNotFound() throws Exception {
    // given
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("refresh:invalid-token")).willReturn(null);

    // when
    Optional<UUID> result = refreshTokenService.findUserId("invalid-token");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("삭제하면 해당 키가 제거된다")
  void deleteRemovesTheKey() throws Exception {
    // given
    String token = "token-to-delete";

    // when
    refreshTokenService.delete(token);

    // then
    verify(redisTemplate).delete("refresh:" + token);
  }
}