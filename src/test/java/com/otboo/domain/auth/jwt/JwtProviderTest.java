package com.otboo.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private JwtProvider jwtProvider;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties(
        "test-secret-key-for-jwt-provider-unit-test-minimum-256-bits",
        900000L,
        604800000L
    );
    jwtProvider = new JwtProvider(properties);
  }

  @Test
  @DisplayName("토큰 발급 후 userId, role, tokenVersion을 정확히 추출한다")
  void extractClaimsFromCreatedToken() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when
    String token = jwtProvider.createAccessToken(userId, "USER", 3L);

    // then
    assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
    assertThat(jwtProvider.getRole(token)).isEqualTo("USER");
    assertThat(jwtProvider.getTokenVersion(token)).isEqualTo(3L);
  }

  @Test
  @DisplayName("정상적으로 발급된 토큰은 유효하다")
  void validTokenIsValid() throws Exception {
    // given
    String token = jwtProvider.createAccessToken(UUID.randomUUID(), "USER", 0L);

    // when & then
    assertThat(jwtProvider.isValid(token)).isTrue();
  }

  @Test
  @DisplayName("잘못된 형식의 토큰은 유효하지 않다")
  void invalidFormatTokenIsNotValid() throws Exception {
    // given
    String invalidToken = "this-is-not-a-valid-jwt";

    // when & then
    assertThat(jwtProvider.isValid(invalidToken)).isFalse();
  }
}